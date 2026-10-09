package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.dao.AcademicDAO;
import com.usamis.dao.StudentDAO;
import com.usamis.model.Models.*;
import com.usamis.util.JsonUtil;
import com.usamis.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * REST API for Students:
 *   GET    /api/students          → list all (with optional ?search=, ?dept=)
 *   GET    /api/students/{id}     → single student
 *   POST   /api/students          → create (requires MANAGE_STUDENTS)
 *   PUT    /api/students/{id}     → update (requires MANAGE_STUDENTS)
 *   DELETE /api/students/{id}     → soft-delete (requires MANAGE_STUDENTS)
 *   GET    /api/students/at-risk  → students below GPA threshold
 *
 * WHY REST: Clean separation — the same endpoints can serve the
 * HTML frontend today and a mobile app tomorrow without change.
 */
@WebServlet(urlPatterns = {"/api/students", "/api/students/*"})
public class StudentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(StudentServlet.class);
    private final StudentDAO  studentDAO  = new StudentDAO();
    private final AcademicDAO academicDAO = new AcademicDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String pathInfo = req.getPathInfo(); // e.g. "/123" or "/at-risk"

        // GET /api/students/at-risk
        // SECURITY: same scoping as the roster. A student may only see THEMSELVES;
        // staff without roster permission get 403. Previously this returned every
        // at-risk student to any authenticated caller (IDOR/PII leak).
        if ("/at-risk".equals(pathInfo)) {
            if ("student".equalsIgnoreCase(user.roleName)) {
                Optional<Student> me = studentDAO.findByUserId(user.id);
                if (me.isEmpty() || me.get().gpa >= 2.5) {
                    JsonUtil.success(resp, List.of());
                } else {
                    JsonUtil.success(resp, List.of(me.get()));
                }
                return;
            }
            if (!canListRoster(user)) { JsonUtil.forbidden(resp); return; }

            double threshold = 2.5;
            try { threshold = Double.parseDouble(req.getParameter("gpa")); } catch (Exception ignored) {}
            JsonUtil.success(resp, studentDAO.findAtRisk(threshold));
            return;
        }

        // GET /api/students/{id}
        if (pathInfo != null && pathInfo.length() > 1) {
            int id = ValidationUtil.parseInt(pathInfo.substring(1), -1);
            if (id < 0) { JsonUtil.badRequest(resp, "Invalid student ID"); return; }

            // Staff without roster permission (lecturer/finance) may only read a
            // student they are entitled to; students only themselves.
            if (!canListRoster(user) && !"student".equalsIgnoreCase(user.roleName)) {
                JsonUtil.forbidden(resp); return;
            }

            Optional<Student> s = studentDAO.findById(id);
            if (s.isEmpty()) { JsonUtil.notFound(resp, "Student"); return; }

            // Students can only view their own record
            if ("student".equalsIgnoreCase(user.roleName) && s.get().userId != null
                    && !s.get().userId.equals(user.id)) {
                JsonUtil.forbidden(resp); return;
            }
            JsonUtil.success(resp, s.get());
            return;
        }

        // GET /api/students — with optional filters
        // SECURITY: a student may only list THEMSELVES. Returning the full roster
        // here leaked every other student's PII (the nav hides the page, but the
        // endpoint must enforce ownership, not the UI).
        if ("student".equalsIgnoreCase(user.roleName)) {
            Optional<Student> me = studentDAO.findByUserId(user.id);
            if (me.isPresent()) {
                JsonUtil.success(resp, List.of(me.get()));
            } else {
                JsonUtil.success(resp, List.of());
            }
            return;
        }

        // Staff without roster permission see nothing (403), not the whole list.
        if (!canListRoster(user)) { JsonUtil.forbidden(resp); return; }

        String search = req.getParameter("search");
        String deptParam = req.getParameter("dept");
        boolean includeInactive = "true".equalsIgnoreCase(req.getParameter("includeInactive"));

        List<Student> students;
        if (search != null && !search.isBlank()) {
            students = studentDAO.search(ValidationUtil.sanitize(search));
        } else if (deptParam != null) {
            int deptId = ValidationUtil.parseInt(deptParam, -1);
            students = deptId > 0 ? studentDAO.findByDepartment(deptId)
                                  : studentDAO.findAll(includeInactive);
        } else {
            students = studentDAO.findAll(includeInactive);
        }

        // Staff list — only roles with a legitimate need may see the roster.
        // (Reached only by non-students; students already returned above.)
        JsonUtil.success(resp, students);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");

        // RBAC check
        if (!hasPermission(user, "MANAGE_STUDENTS")) {
            auditDenied(user, "Students", req);
            JsonUtil.forbidden(resp); return;
        }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject json = JsonUtil.fromJson(body, JsonObject.class);
        if (json == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        // Extract and validate fields
        String studentId = getString(json, "studentId");
        String firstName = getString(json, "firstName");
        String lastName  = getString(json, "lastName");
        String email     = getString(json, "email");
        String dept      = getString(json, "departmentId");
        String prog      = getString(json, "programId");
        String year      = getString(json, "yearOfStudy");

        if (ValidationUtil.isBlank(studentId) || ValidationUtil.isBlank(firstName)
                || ValidationUtil.isBlank(lastName) || ValidationUtil.isBlank(email)) {
            JsonUtil.badRequest(resp, "studentId, firstName, lastName, email are required."); return;
        }
        if (!ValidationUtil.isValidEmail(email)) {
            JsonUtil.badRequest(resp, "Invalid email address."); return;
        }
        if (studentDAO.existsByStudentId(studentId)) {
            JsonUtil.conflict(resp, "Student ID '" + studentId + "' already exists."); return;
        }
        if (studentDAO.existsByEmail(email)) {
            JsonUtil.conflict(resp, "Email '" + email + "' is already registered."); return;
        }

        Student s = new Student();
        s.studentId    = ValidationUtil.sanitize(studentId);
        s.firstName    = ValidationUtil.sanitize(firstName);
        s.lastName     = ValidationUtil.sanitize(lastName);
        s.email        = email.trim().toLowerCase();
        s.phone        = ValidationUtil.sanitize(getString(json, "phone"));
        s.gender       = getString(json, "gender");
        s.nationality  = getString(json, "nationality");
        s.departmentId = ValidationUtil.parseInt(dept, 1);
        s.programId    = ValidationUtil.parseInt(prog, 1);
        s.yearOfStudy  = ValidationUtil.parseInt(year, 1);
        s.status       = "Active";
        String dob     = getString(json, "dateOfBirth");
        try { if (dob != null && !dob.isBlank()) s.dateOfBirth = LocalDate.parse(dob); }
        catch (Exception ignored) {}

        try {
            int newId = studentDAO.create(s);
            academicDAO.log(user.id, user.username, user.roleName, "CREATE", "Students",
                "Added student " + s.studentId + " — " + s.fullName(), getIp(req), null);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("id", newId);
            result.addProperty("message", "Student created successfully.");
            JsonUtil.ok(resp, result);
        } catch (Exception e) {
            log.error("Create student failed", e);
            JsonUtil.serverError(resp, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!hasPermission(user, "MANAGE_STUDENTS")) { JsonUtil.forbidden(resp); return; }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() < 2) { JsonUtil.badRequest(resp, "Student ID required"); return; }
        int id = ValidationUtil.parseInt(pathInfo.substring(1), -1);
        if (id < 0) { JsonUtil.badRequest(resp, "Invalid ID"); return; }

        Optional<Student> existing = studentDAO.findById(id);
        if (existing.isEmpty()) { JsonUtil.notFound(resp, "Student"); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject json = JsonUtil.fromJson(body, JsonObject.class);
        if (json == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        Student s = existing.get();
        if (json.has("firstName"))    s.firstName    = ValidationUtil.sanitize(json.get("firstName").getAsString());
        if (json.has("lastName"))     s.lastName     = ValidationUtil.sanitize(json.get("lastName").getAsString());
        if (json.has("email"))        s.email        = json.get("email").getAsString().trim().toLowerCase();
        if (json.has("phone"))        s.phone        = json.get("phone").getAsString();
        if (json.has("gender"))       s.gender       = json.get("gender").getAsString();
        if (json.has("departmentId")) s.departmentId = json.get("departmentId").getAsInt();
        if (json.has("programId"))    s.programId    = json.get("programId").getAsInt();
        if (json.has("yearOfStudy"))  s.yearOfStudy  = json.get("yearOfStudy").getAsInt();
        if (json.has("status"))       s.status       = json.get("status").getAsString();
        if (json.has("nationality"))  s.nationality  = json.get("nationality").getAsString();

        if (!ValidationUtil.isValidEmail(s.email)) { JsonUtil.badRequest(resp, "Invalid email"); return; }

        boolean ok = studentDAO.update(s);
        if (ok) {
            academicDAO.log(user.id, user.username, user.roleName, "UPDATE", "Students",
                "Updated student " + s.studentId, getIp(req), null);
            JsonUtil.success(resp, "Student updated.");
        } else {
            JsonUtil.serverError(resp, "Update failed.");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!hasPermission(user, "MANAGE_STUDENTS")) { JsonUtil.forbidden(resp); return; }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() < 2) { JsonUtil.badRequest(resp, "Student ID required"); return; }
        int id = ValidationUtil.parseInt(pathInfo.substring(1), -1);
        if (id < 0) { JsonUtil.badRequest(resp, "Invalid ID"); return; }

        Optional<Student> s = studentDAO.findById(id);
        if (s.isEmpty()) { JsonUtil.notFound(resp, "Student"); return; }

        boolean ok = studentDAO.delete(id);  // soft delete
        if (ok) {
            academicDAO.log(user.id, user.username, user.roleName, "DELETE", "Students",
                "Deactivated student " + s.get().studentId, getIp(req), null);
            JsonUtil.success(resp, "Student deactivated.");
        } else {
            JsonUtil.serverError(resp, "Delete failed.");
        }
    }

    // ─── HELPERS ────────────────────────────────────────────
    /**
     * Only roles that legitimately manage the student roster may read it in
     * bulk or read an arbitrary student by id. Lecturers and finance have no
     * student-management permission, so they must not receive the roster.
     */
    private boolean canListRoster(UserDTO user) {
        if (user == null || user.roleName == null) return false;
        String r = user.roleName.toLowerCase();
        return "admin".equals(r) || "registrar".equals(r);
    }

    private boolean hasPermission(UserDTO user, String perm) {
        // Cached in session for performance — checked against DB on first load
        // For this implementation: role-based inline check
        return switch (user.roleName.toLowerCase()) {
            case "admin"      -> true;
            case "registrar"  -> "MANAGE_STUDENTS".equals(perm) || "VIEW_GRADES".equals(perm);
            case "lecturer"   -> "ENTER_GRADES".equals(perm) || "VIEW_GRADES".equals(perm);
            case "finance"    -> "MANAGE_FEES".equals(perm) || "VIEW_FEES".equals(perm);
            case "student"    -> "VIEW_GRADES".equals(perm) || "VIEW_FEES".equals(perm) || "VIEW_OWN_PROFILE".equals(perm);
            default           -> false;
        };
    }

    private void auditDenied(UserDTO user, String module, HttpServletRequest req) {
        academicDAO.log(user.id, user.username, user.roleName, "ACCESS_DENIED", module,
            "Permission denied for " + module, getIp(req), null);
    }

    private String getString(JsonObject j, String key) {
        return j.has(key) && !j.get(key).isJsonNull() ? j.get(key).getAsString() : null;
    }

    private String getIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
