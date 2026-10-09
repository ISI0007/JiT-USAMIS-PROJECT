package com.usamis.dao;

import com.usamis.model.Models.*;
import com.usamis.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentDAO {

    private static final Logger log = LoggerFactory.getLogger(StudentDAO.class);

    private static final String BASE_SELECT =
        "SELECT s.*, d.name AS dept_name, p.name AS prog_name, " +
        "       ROUND(AVG(g.gpa_points)::NUMERIC, 2) AS gpa " +
        "FROM students s " +
        "JOIN departments d ON s.department_id = d.id " +
        "JOIN programs p    ON s.program_id = p.id " +
        "LEFT JOIN enrollments e ON e.student_id = s.id " +
        "LEFT JOIN grades g      ON g.enrollment_id = e.id ";

    // ─── READ ─────────────────────────────────────────────────
    public List<Student> findAll() {
        String sql = BASE_SELECT + "GROUP BY s.id, d.name, p.name ORDER BY s.student_id";
        return executeQuery(sql);
    }

    public List<Student> findByDepartment(int deptId) {
        String sql = BASE_SELECT + "WHERE s.department_id = ? GROUP BY s.id, d.name, p.name";
        return executeQueryWithInt(sql, deptId);
    }

    public List<Student> findAtRisk(double gpaThreshold) {
        // Students whose average GPA falls below threshold
        String sql = BASE_SELECT +
            "GROUP BY s.id, d.name, p.name " +
            "HAVING ROUND(AVG(g.gpa_points)::NUMERIC, 2) < ? " +
            "ORDER BY gpa ASC NULLS LAST";
        List<Student> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, gpaThreshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapStudent(rs));
            }
        } catch (SQLException e) {
            log.error("findAtRisk", e);
        }
        return list;
    }

    public Optional<Student> findById(int id) {
        String sql = BASE_SELECT + "WHERE s.id = ? GROUP BY s.id, d.name, p.name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapStudent(rs));
            }
        } catch (SQLException e) {
            log.error("findById student {}", id, e);
        }
        return Optional.empty();
    }

    public Optional<Student> findByUserId(int userId) {
        String sql = BASE_SELECT + "WHERE s.user_id = ? GROUP BY s.id, d.name, p.name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapStudent(rs));
            }
        } catch (SQLException e) {
            log.error("findByUserId {}", userId, e);
        }
        return Optional.empty();
    }

    public Optional<Student> findByStudentId(String studentId) {
        String sql = BASE_SELECT + "WHERE s.student_id = ? GROUP BY s.id, d.name, p.name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapStudent(rs));
            }
        } catch (SQLException e) {
            log.error("findByStudentId {}", studentId, e);
        }
        return Optional.empty();
    }

    public boolean existsByStudentId(String studentId) {
        String sql = "SELECT 1 FROM students WHERE student_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            log.error("existsByStudentId", e);
            return false;
        }
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM students WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            return false;
        }
    }

    // ─── WRITE ────────────────────────────────────────────────
    public int create(Student s) throws SQLException {
        String sql = "INSERT INTO students (student_id, user_id, first_name, last_name, " +
                     "date_of_birth, gender, email, phone, department_id, program_id, " +
                     "year_of_study, status, nationality) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.studentId);
            if (s.userId != null) ps.setInt(2, s.userId); else ps.setNull(2, Types.INTEGER);
            ps.setString(3, s.firstName);
            ps.setString(4, s.lastName);
            if (s.dateOfBirth != null) ps.setDate(5, Date.valueOf(s.dateOfBirth)); else ps.setNull(5, Types.DATE);
            ps.setString(6, s.gender);
            ps.setString(7, s.email);
            ps.setString(8, s.phone);
            ps.setInt(9, s.departmentId);
            ps.setInt(10, s.programId);
            ps.setInt(11, s.yearOfStudy);
            ps.setString(12, s.status != null ? s.status : "Active");
            ps.setString(13, s.nationality != null ? s.nationality : "Chinese");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int newId = rs.getInt(1);
                log.info("Created student id={} studentId={}", newId, s.studentId);
                return newId;
            }
        }
    }

    public boolean update(Student s) {
        String sql = "UPDATE students SET first_name=?, last_name=?, date_of_birth=?, " +
                     "gender=?, email=?, phone=?, department_id=?, program_id=?, " +
                     "year_of_study=?, status=?, nationality=? WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.firstName);
            ps.setString(2, s.lastName);
            if (s.dateOfBirth != null) ps.setDate(3, Date.valueOf(s.dateOfBirth)); else ps.setNull(3, Types.DATE);
            ps.setString(4, s.gender);
            ps.setString(5, s.email);
            ps.setString(6, s.phone);
            ps.setInt(7, s.departmentId);
            ps.setInt(8, s.programId);
            ps.setInt(9, s.yearOfStudy);
            ps.setString(10, s.status);
            ps.setString(11, s.nationality);
            ps.setInt(12, s.id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("update student {}", s.id, e);
            return false;
        }
    }

    public boolean delete(int id) {
        // Soft delete — preserve data integrity
        String sql = "UPDATE students SET status = 'Inactive' WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("delete student {}", id, e);
            return false;
        }
    }

    // ─── SEARCH ──────────────────────────────────────────────
    public List<Student> search(String query) {
        String sql = BASE_SELECT +
            "WHERE s.student_id ILIKE ? OR s.first_name ILIKE ? OR s.last_name ILIKE ? " +
            "   OR s.email ILIKE ? " +
            "GROUP BY s.id, d.name, p.name ORDER BY s.student_id LIMIT 50";
        String q = "%" + query + "%";
        List<Student> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q); ps.setString(2, q);
            ps.setString(3, q); ps.setString(4, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapStudent(rs));
            }
        } catch (SQLException e) {
            log.error("search students", e);
        }
        return list;
    }

    // ─── STATS ───────────────────────────────────────────────
    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM students WHERE status = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────
    private List<Student> executeQuery(String sql) {
        List<Student> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapStudent(rs));
        } catch (SQLException e) {
            log.error("executeQuery", e);
        }
        return list;
    }

    private List<Student> executeQueryWithInt(String sql, int param) {
        List<Student> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapStudent(rs));
            }
        } catch (SQLException e) {
            log.error("executeQueryWithInt", e);
        }
        return list;
    }

    private Student mapStudent(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.id             = rs.getInt("id");
        s.studentId      = rs.getString("student_id");
        int uid = rs.getInt("user_id"); s.userId = rs.wasNull() ? null : uid;
        s.firstName      = rs.getString("first_name");
        s.lastName       = rs.getString("last_name");
        Date dob         = rs.getDate("date_of_birth");
        s.dateOfBirth    = dob != null ? dob.toLocalDate() : null;
        s.gender         = rs.getString("gender");
        s.email          = rs.getString("email");
        s.phone          = rs.getString("phone");
        s.departmentId   = rs.getInt("department_id");
        s.departmentName = rs.getString("dept_name");
        s.programId      = rs.getInt("program_id");
        s.programName    = rs.getString("prog_name");
        s.yearOfStudy    = rs.getInt("year_of_study");
        Date ed          = rs.getDate("enrollment_date");
        s.enrollmentDate = ed != null ? ed.toLocalDate() : null;
        s.status         = rs.getString("status");
        s.nationality    = rs.getString("nationality");
        Timestamp ca     = rs.getTimestamp("created_at");
        s.createdAt      = ca != null ? ca.toLocalDateTime() : null;
        // GPA from LEFT JOIN aggregate (may be null if no grades)
        double gpa = rs.getDouble("gpa");
        s.gpa = rs.wasNull() ? null : gpa;
        return s;
    }
}
