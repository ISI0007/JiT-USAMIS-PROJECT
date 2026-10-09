package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.dao.AcademicDAO;
import com.usamis.dao.StudentDAO;
import com.usamis.dao.UserDAO;
import com.usamis.model.Models.Student;
import com.usamis.model.Models.User;
import com.usamis.model.Models.UserDTO;
import com.usamis.util.DatabaseConnection;
import com.usamis.util.JsonUtil;
import com.usamis.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * POST /api/auth/register — student self-registration.
 *
 * Creates BOTH rows atomically:
 *   1. a `users` row (username + bcrypt-hashed password) with the student role
 *   2. a linked `students` row (user_id -> users.id) so academic features work
 *
 * WHY a dedicated servlet: self-registration is intentionally limited to the
 * student role and must never be reachable with elevated roles. Keeping it
 * separate from the staff user-management servlet makes that boundary explicit
 * and auditable.
 *
 * SECURITY:
 *  - role is forced server-side to "student"; the client cannot request admin.
 *  - password is bcrypt-hashed by UserDAO (never stored or logged in plaintext).
 *  - duplicate username/email/studentId are rejected with 409, not a 500.
 *  - the whole thing runs in one transaction: an orphan users row is never left
 *    behind if the student insert fails.
 */
@WebServlet(urlPatterns = {"/api/auth/register"})
public class RegisterServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(RegisterServlet.class);

    private final UserDAO     userDAO     = new UserDAO();
    private final StudentDAO  studentDAO  = new StudentDAO();
    private final AcademicDAO academicDAO = new AcademicDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body;
        try (var reader = req.getReader()) {
            body = reader.lines().reduce("", String::concat);
        }
        JsonObject json = JsonUtil.fromJson(body, JsonObject.class);
        if (json == null) { JsonUtil.badRequest(resp, "Invalid JSON body"); return; }

        String username  = str(json, "username");
        String password  = json.has("password") ? json.get("password").getAsString() : "";
        String firstName = str(json, "firstName");
        String lastName  = str(json, "lastName");
        String email     = str(json, "email");
        String studentNo = str(json, "studentId");
        int departmentId = ValidationUtil.parseInt(str(json, "departmentId"), 0);
        int programId    = ValidationUtil.parseInt(str(json, "programId"), 0);
        int yearOfStudy  = ValidationUtil.parseInt(str(json, "yearOfStudy"), 1);

        // ── validation (server-side, never trust the client) ──
        if (ValidationUtil.isBlank(username) || ValidationUtil.isBlank(password)
                || ValidationUtil.isBlank(firstName) || ValidationUtil.isBlank(lastName)) {
            JsonUtil.badRequest(resp, "Username, password, first name and last name are required.");
            return;
        }
        if (username.trim().length() < 3) {
            JsonUtil.badRequest(resp, "Username must be at least 3 characters.");
            return;
        }
        if (password.length() < 6) {
            JsonUtil.badRequest(resp, "Password must be at least 6 characters.");
            return;
        }
        if (!ValidationUtil.isValidEmail(email)) {
            JsonUtil.badRequest(resp, "A valid email address is required.");
            return;
        }
        if (!ValidationUtil.isValidStudentId(studentNo)) {
            JsonUtil.badRequest(resp, "Student ID must look like STU2024001 (STU + 7 digits).");
            return;
        }
        if (departmentId <= 0 || programId <= 0) {
            JsonUtil.badRequest(resp, "A department and program must be selected.");
            return;
        }
        if (yearOfStudy < 1 || yearOfStudy > 6) {
            JsonUtil.badRequest(resp, "Year of study must be between 1 and 6.");
            return;
        }

        // ── duplicate checks ──
        if (userDAO.findByUsername(username.trim().toLowerCase()).isPresent()) {
            JsonUtil.conflict(resp, "That username is already taken.");
            return;
        }
        if (studentDAO.existsByStudentId(studentNo.trim())) {
            JsonUtil.conflict(resp, "That student ID is already registered.");
            return;
        }
        if (studentDAO.existsByEmail(email.trim().toLowerCase())) {
            JsonUtil.conflict(resp, "That email is already registered.");
            return;
        }

        int studentRoleId;
        try {
            studentRoleId = roleId("student");
        } catch (SQLException e) {
            log.error("registration: could not resolve student role", e);
            JsonUtil.serverError(resp, "Registration is temporarily unavailable.");
            return;
        }

        // ── create users + students atomically ──
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            int userId = insertUser(conn, username, password, firstName, lastName, email, studentRoleId);
            int studentPk = insertStudent(conn, studentNo, userId, firstName, lastName, email, departmentId, programId, yearOfStudy);

            conn.commit();

            academicDAO.log(userId, username.trim().toLowerCase(), "student", "CREATE", "Auth",
                "Self-registered student account (students.id=" + studentPk + ")", getClientIp(req),
                req.getHeader("User-Agent"));

            // Auto-login: hand back a session so the new student lands in the app.
            UserDTO dto = new UserDTO();
            dto.id        = userId;
            dto.username  = username.trim().toLowerCase();
            dto.firstName = firstName.trim();
            dto.lastName  = lastName.trim();
            dto.email     = email.trim().toLowerCase();
            dto.roleId    = studentRoleId;
            dto.roleName  = "Student";
            dto.status    = "active";

            HttpSession session = req.getSession(true);
            session.setAttribute("user", dto);
            session.setAttribute("roleId", studentRoleId);
            session.setMaxInactiveInterval(60 * 60);

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Account created. You are now signed in.");
            response.add("user", JsonUtil.toJsonElement(dto));
            JsonUtil.ok(resp, response);

            log.info("Student self-registered: username={} studentId={}", username, studentNo);
        } catch (SQLException e) {
            if (conn != null) { try { conn.rollback(); } catch (SQLException ignored) {} }
            // Unique-violation race: another request created the same row first.
            if (isUniqueViolation(e)) {
                JsonUtil.conflict(resp, "That username, student ID or email is already registered.");
                return;
            }
            log.error("registration failed for username={}", username, e);
            JsonUtil.serverError(resp, "Could not create the account. Please try again.");
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    private int insertUser(Connection conn, String username, String rawPassword, String first,
                           String last, String email, int roleId) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, first_name, last_name, email, role_id, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'active') RETURNING id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            ps.setString(2, com.usamis.util.PasswordUtil.hash(rawPassword));
            ps.setString(3, ValidationUtil.sanitize(first));
            ps.setString(4, ValidationUtil.sanitize(last));
            ps.setString(5, email.trim().toLowerCase());
            ps.setInt(6, roleId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private int insertStudent(Connection conn, String studentNo, int userId, String first, String last,
                              String email, int deptId, int programId, int year) throws SQLException {
        String sql = "INSERT INTO students (student_id, user_id, first_name, last_name, email, " +
                     "department_id, program_id, year_of_study, status, nationality) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Active', 'Chinese') RETURNING id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentNo.trim());
            ps.setInt(2, userId);
            ps.setString(3, ValidationUtil.sanitize(first));
            ps.setString(4, ValidationUtil.sanitize(last));
            ps.setString(5, email.trim().toLowerCase());
            ps.setInt(6, deptId);
            ps.setInt(7, programId);
            ps.setInt(8, year);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private int roleId(String name) throws SQLException {
        String sql = "SELECT id FROM roles WHERE name = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("role not found: " + name);
    }

    private static boolean isUniqueViolation(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("23");
    }

    private static String str(JsonObject o, String k) {
        try { return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : ""; }
        catch (Exception e) { return ""; }
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
