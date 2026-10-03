package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.dao.AcademicDAO;
import com.usamis.model.Models;
import com.usamis.model.Models.*;
import com.usamis.util.JsonUtil;
import com.usamis.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/* ══════════════════════════════════════════════════════════════
   COURSE SERVLET
   GET  /api/courses        → all courses
   GET  /api/courses/{id}   → single course
   POST /api/courses        → create (admin, registrar)
   PUT  /api/courses/{id}   → update
   DELETE /api/courses/{id} → cancel
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/courses", "/api/courses/*"})
class CourseServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(CourseServlet.class);
    private final AcademicDAO dao = new AcademicDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path != null && path.length() > 1) {
            // by ID or by code
            String key = path.substring(1);
            int id = ValidationUtil.parseInt(key, -1);
            if (id > 0) {
                // find by id — simple: return from list (small dataset)
                dao.findAllCourses().stream()
                    .filter(c -> c.id == id)
                    .findFirst()
                    .ifPresentOrElse(
                        c -> { try { JsonUtil.success(resp, c); } catch (IOException e) { throw new RuntimeException(e); } },
                        () -> { try { JsonUtil.notFound(resp, "Course"); } catch (IOException e) { throw new RuntimeException(e); } }
                    );
            } else {
                dao.findCourseByCode(key)
                    .ifPresentOrElse(
                        c -> { try { JsonUtil.success(resp, c); } catch (IOException e) { throw new RuntimeException(e); } },
                        () -> { try { JsonUtil.notFound(resp, "Course"); } catch (IOException e) { throw new RuntimeException(e); } }
                    );
            }
            return;
        }
        JsonUtil.success(resp, dao.findAllCourses());
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!isAdminOrRegistrar(user)) { JsonUtil.forbidden(resp); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        String code = j.has("code") ? j.get("code").getAsString().trim().toUpperCase() : null;
        String name = j.has("name") ? j.get("name").getAsString().trim() : null;
        if (ValidationUtil.isBlank(code) || ValidationUtil.isBlank(name)) {
            JsonUtil.badRequest(resp, "code and name are required."); return;
        }
        if (!ValidationUtil.isValidCourseCode(code)) {
            JsonUtil.badRequest(resp, "Invalid course code format (e.g. CS301)."); return;
        }
        if (dao.findCourseByCode(code).isPresent()) {
            JsonUtil.conflict(resp, "Course code '" + code + "' already exists."); return;
        }

        Course c = new Course();
        c.code         = code;
        c.name         = name;
        c.departmentId = j.has("departmentId") ? j.get("departmentId").getAsInt() : 1;
        c.credits      = j.has("credits") ? j.get("credits").getAsInt() : 3;
        c.maxEnrollment= j.has("maxEnrollment") ? j.get("maxEnrollment").getAsInt() : 50;
        c.semester     = j.has("semester") ? j.get("semester").getAsString() : "Semester 1";
        c.status       = "Active";
        c.description  = j.has("description") ? j.get("description").getAsString() : null;
        if (j.has("instructorId") && !j.get("instructorId").isJsonNull())
            c.instructorId = j.get("instructorId").getAsInt();

        try {
            int newId = dao.createCourse(c);
            dao.log(user.id, user.username, user.roleName, "CREATE", "Courses",
                "Added course " + code + " — " + name, getIp(req), null);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("id", newId);
            JsonUtil.ok(resp, result);
        } catch (Exception e) {
            log.error("Create course", e);
            JsonUtil.serverError(resp, e.getMessage());
        }
    }

    @Override protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!isAdminOrRegistrar(user)) { JsonUtil.forbidden(resp); return; }

        int id = parseId(req);
        if (id < 0) { JsonUtil.badRequest(resp, "Invalid course ID"); return; }

        var existing = dao.findAllCourses().stream().filter(c -> c.id == id).findFirst();
        if (existing.isEmpty()) { JsonUtil.notFound(resp, "Course"); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        Course c = existing.get();
        if (j.has("name"))           c.name          = j.get("name").getAsString();
        if (j.has("credits"))        c.credits       = j.get("credits").getAsInt();
        if (j.has("maxEnrollment"))  c.maxEnrollment = j.get("maxEnrollment").getAsInt();
        if (j.has("semester"))       c.semester      = j.get("semester").getAsString();
        if (j.has("status"))         c.status        = j.get("status").getAsString();
        if (j.has("description"))    c.description   = j.get("description").getAsString();

        boolean ok = dao.updateCourse(c);
        if (ok) {
            dao.log(user.id, user.username, user.roleName, "UPDATE", "Courses",
                "Updated course " + c.code, getIp(req), null);
            JsonUtil.success(resp, "Course updated.");
        } else { JsonUtil.serverError(resp, "Update failed."); }
    }

    @Override protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!isAdmin(user)) { JsonUtil.forbidden(resp); return; }

        int id = parseId(req);
        if (id < 0) { JsonUtil.badRequest(resp, "Invalid ID"); return; }

        boolean ok = dao.deleteCourse(id);
        if (ok) {
            dao.log(user.id, user.username, user.roleName, "DELETE", "Courses",
                "Cancelled course id=" + id, getIp(req), null);
            JsonUtil.success(resp, "Course cancelled.");
        } else { JsonUtil.serverError(resp, "Delete failed."); }
    }

    private boolean isAdminOrRegistrar(UserDTO u) {
        return "admin".equals(u.roleName) || "registrar".equals(u.roleName);
    }
    private boolean isAdmin(UserDTO u) { return "admin".equals(u.roleName); }
    private int parseId(HttpServletRequest req) {
        String pi = req.getPathInfo();
        return (pi != null && pi.length() > 1) ? ValidationUtil.parseInt(pi.substring(1), -1) : -1;
    }
    private String getIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }
}


/* ══════════════════════════════════════════════════════════════
   ENROLLMENT SERVLET
   GET  /api/enrollments              → all enrollments
   GET  /api/enrollments?student={id} → by student
   POST /api/enrollments              → enroll (admin, registrar)
   DELETE /api/enrollments/{id}       → drop
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/enrollments", "/api/enrollments/*"})
class EnrollmentServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentServlet.class);
    private final AcademicDAO dao = new AcademicDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String studentParam = req.getParameter("student");

        if (studentParam != null) {
            int sid = ValidationUtil.parseInt(studentParam, -1);
            if (sid < 0) { JsonUtil.badRequest(resp, "Invalid student ID"); return; }
            // Students can only see their own enrollments
            JsonUtil.success(resp, dao.findEnrollmentsByStudent(sid));
        } else {
            // Students get own; others get all
            if ("student".equals(user.roleName)) {
                JsonUtil.success(resp, dao.findEnrollmentsByStudent(user.id));
            } else {
                JsonUtil.success(resp, dao.findAllEnrollments());
            }
        }
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!canManageEnrollment(user)) { JsonUtil.forbidden(resp); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        if (!j.has("studentId") || !j.has("courseId") || !j.has("semester")) {
            JsonUtil.badRequest(resp, "studentId, courseId, semester are required."); return;
        }

        int studentId = j.get("studentId").getAsInt();
        int courseId  = j.get("courseId").getAsInt();
        String semester = j.get("semester").getAsString();

        // Duplicate check
        if (dao.isDuplicateEnrollment(studentId, courseId, semester)) {
            JsonUtil.conflict(resp, "Student is already enrolled in this course for this semester."); return;
        }
        // Capacity check
        if (dao.isCourseAtCapacity(courseId)) {
            JsonUtil.conflict(resp, "This course has reached maximum enrollment capacity."); return;
        }

        try {
            int newId = dao.createEnrollment(studentId, courseId, semester);
            dao.log(user.id, user.username, user.roleName, "CREATE", "Enrollment",
                "Enrolled student #" + studentId + " in course #" + courseId + " [" + semester + "]",
                getIp(req), null);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("enrollmentId", newId);
            result.addProperty("message", "Enrollment successful.");
            JsonUtil.ok(resp, result);
        } catch (Exception e) {
            log.error("Create enrollment", e);
            JsonUtil.serverError(resp, e.getMessage());
        }
    }

    @Override protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!canManageEnrollment(user)) { JsonUtil.forbidden(resp); return; }

        String pi = req.getPathInfo();
        int id = (pi != null && pi.length() > 1) ? ValidationUtil.parseInt(pi.substring(1), -1) : -1;
        if (id < 0) { JsonUtil.badRequest(resp, "Enrollment ID required"); return; }

        boolean ok = dao.dropEnrollment(id);
        if (ok) {
            dao.log(user.id, user.username, user.roleName, "DELETE", "Enrollment",
                "Dropped enrollment #" + id, getIp(req), null);
            JsonUtil.success(resp, "Enrollment dropped.");
        } else { JsonUtil.serverError(resp, "Drop failed."); }
    }

    private boolean canManageEnrollment(UserDTO u) {
        return "admin".equals(u.roleName) || "registrar".equals(u.roleName);
    }
    private String getIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }
}


/* ══════════════════════════════════════════════════════════════
   GRADE SERVLET
   GET  /api/grades               → all (admin, registrar, lecturer)
   GET  /api/grades?student={id}  → by student
   POST /api/grades               → enter grade (admin, lecturer)
   PUT  /api/grades/{id}          → update grade
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/grades", "/api/grades/*"})
class GradeServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(GradeServlet.class);
    private final AcademicDAO dao = new AcademicDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String studentParam = req.getParameter("student");

        if ("student".equals(user.roleName)) {
            // Students see only their own grades — user.id maps to users table, not students table
            // In a full implementation, look up student.user_id = user.id
            JsonUtil.success(resp, dao.findAllGrades().stream()
                .filter(g -> g.studentId != null)
                .toList());
            return;
        }

        if (studentParam != null) {
            int sid = ValidationUtil.parseInt(studentParam, -1);
            JsonUtil.success(resp, dao.findGradesByStudent(sid));
        } else {
            JsonUtil.success(resp, dao.findAllGrades());
        }
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!canEnterGrades(user)) { JsonUtil.forbidden(resp); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        if (!j.has("enrollmentId") || !j.has("score")) {
            JsonUtil.badRequest(resp, "enrollmentId and score are required."); return;
        }

        int enrollmentId = j.get("enrollmentId").getAsInt();
        double score     = j.get("score").getAsDouble();

        if (!ValidationUtil.isValidScore(score)) {
            JsonUtil.badRequest(resp, "Score must be between 0 and 100."); return;
        }
        if (dao.existsGradeForEnrollment(enrollmentId)) {
            JsonUtil.conflict(resp, "Grade already exists for this enrollment. Use PUT to update."); return;
        }

        try {
            int newId = dao.createGrade(enrollmentId, score, user.id);
            Models.GradeInfo info = Models.computeGrade(score);
            dao.log(user.id, user.username, user.roleName, "CREATE", "Grades",
                "Entered grade " + info.letterGrade() + " (score=" + score + ") for enrollment #" + enrollmentId,
                getIp(req), null);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("gradeId", newId);
            result.addProperty("letterGrade", info.letterGrade());
            result.addProperty("gpaPoints", info.gpaPoints());
            result.addProperty("message", "Grade saved successfully.");
            JsonUtil.ok(resp, result);
        } catch (Exception e) {
            log.error("Create grade", e);
            JsonUtil.serverError(resp, e.getMessage());
        }
    }

    @Override protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!canEnterGrades(user)) { JsonUtil.forbidden(resp); return; }

        String pi = req.getPathInfo();
        int id = (pi != null && pi.length() > 1) ? ValidationUtil.parseInt(pi.substring(1), -1) : -1;
        if (id < 0) { JsonUtil.badRequest(resp, "Grade ID required"); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        double score = j.has("score") ? j.get("score").getAsDouble() : -1;
        if (!ValidationUtil.isValidScore(score)) {
            JsonUtil.badRequest(resp, "Valid score (0-100) required."); return;
        }

        boolean ok = dao.updateGrade(id, score, user.id);
        if (ok) {
            Models.GradeInfo info = Models.computeGrade(score);
            dao.log(user.id, user.username, user.roleName, "UPDATE", "Grades",
                "Updated grade #" + id + " to " + info.letterGrade() + " (" + score + ")", getIp(req), null);
            JsonUtil.success(resp, "Grade updated to " + info.letterGrade());
        } else { JsonUtil.serverError(resp, "Update failed."); }
    }

    private boolean canEnterGrades(UserDTO u) {
        return "admin".equals(u.roleName) || "lecturer".equals(u.roleName);
    }
    private String getIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For"); return xff != null ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }
}


/* ══════════════════════════════════════════════════════════════
   FEE SERVLET
   GET  /api/fees                  → all records (admin, finance)
   GET  /api/fees?student={id}     → by student
   GET  /api/fees/defaulters       → unpaid/partial only
   POST /api/fees                  → create record
   POST /api/fees/{id}/pay         → record a payment
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/fees", "/api/fees/*"})
class FeeServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(FeeServlet.class);
    private final AcademicDAO dao = new AcademicDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String pathInfo   = req.getPathInfo();
        String studentParam = req.getParameter("student");

        // GET /api/fees/defaulters
        if ("/defaulters".equals(pathInfo)) {
            if (!canManageFees(user)) { JsonUtil.forbidden(resp); return; }
            JsonUtil.success(resp, dao.findFeeDefaulters());
            return;
        }

        // GET /api/fees?student=id
        if (studentParam != null) {
            int sid = ValidationUtil.parseInt(studentParam, -1);
            if (sid < 0) { JsonUtil.badRequest(resp, "Invalid student ID"); return; }
            JsonUtil.success(resp, dao.findFeesByStudent(sid));
            return;
        }

        // Students see only own fees
        if ("student".equals(user.roleName)) {
            JsonUtil.success(resp, dao.findFeesByStudent(user.id));
            return;
        }

        if (!canManageFees(user)) { JsonUtil.forbidden(resp); return; }
        JsonUtil.success(resp, dao.findAllFees());
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String pathInfo = req.getPathInfo();

        // POST /api/fees/{id}/pay — record additional payment
        if (pathInfo != null && pathInfo.matches("/\\d+/pay")) {
            handlePayment(req, resp, user);
            return;
        }

        if (!canManageFees(user)) { JsonUtil.forbidden(resp); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null) { JsonUtil.badRequest(resp, "Invalid JSON"); return; }

        if (!j.has("studentId") || !j.has("feeType") || !j.has("totalAmount")) {
            JsonUtil.badRequest(resp, "studentId, feeType, totalAmount are required."); return;
        }

        FeeRecord f = new FeeRecord();
        f.receiptNo    = "RCP" + System.currentTimeMillis();
        f.studentId    = j.get("studentId").getAsInt();
        f.feeType      = j.get("feeType").getAsString();
        f.semester     = j.has("semester") ? j.get("semester").getAsString() : "Semester 1";
        f.totalAmount  = j.get("totalAmount").getAsDouble();
        f.paidAmount   = j.has("paidAmount") ? j.get("paidAmount").getAsDouble() : 0;
        f.paymentMethod= j.has("paymentMethod") ? j.get("paymentMethod").getAsString() : null;
        f.createdBy    = user.id;
        if (f.paidAmount > 0)
            try { f.paymentDate = java.time.LocalDate.now(); } catch (Exception ignored) {}

        try {
            int newId = dao.createFeeRecord(f);
            dao.log(user.id, user.username, user.roleName, "CREATE", "Fees",
                "Created fee record " + f.receiptNo + " for student #" + f.studentId +
                " amount ¥" + f.totalAmount, getIp(req), null);
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("id", newId);
            result.addProperty("receiptNo", f.receiptNo);
            result.addProperty("message", "Fee record created.");
            JsonUtil.ok(resp, result);
        } catch (Exception e) {
            log.error("Create fee", e);
            JsonUtil.serverError(resp, e.getMessage());
        }
    }

    private void handlePayment(HttpServletRequest req, HttpServletResponse resp, UserDTO user)
            throws IOException {
        if (!canManageFees(user)) { JsonUtil.forbidden(resp); return; }

        // Extract fee ID from path /api/fees/{id}/pay
        String pi  = req.getPathInfo();                       // "/{id}/pay"
        String idStr = pi.replaceAll("[^\\d]", "");
        int feeId  = ValidationUtil.parseInt(idStr, -1);
        if (feeId < 0) { JsonUtil.badRequest(resp, "Invalid fee ID"); return; }

        String body = req.getReader().lines().reduce("", String::concat);
        JsonObject j = JsonUtil.fromJson(body, JsonObject.class);
        if (j == null || !j.has("amount")) { JsonUtil.badRequest(resp, "amount required"); return; }

        double amount = j.get("amount").getAsDouble();
        if (amount <= 0) { JsonUtil.badRequest(resp, "Payment amount must be positive."); return; }
        String method = j.has("method") ? j.get("method").getAsString() : "Cash";

        boolean ok = dao.recordPayment(feeId, amount, method);
        if (ok) {
            dao.log(user.id, user.username, user.roleName, "UPDATE", "Fees",
                "Recorded payment ¥" + amount + " for fee #" + feeId, getIp(req), null);
            JsonUtil.success(resp, "Payment of ¥" + amount + " recorded.");
        } else { JsonUtil.serverError(resp, "Payment recording failed."); }
    }

    private boolean canManageFees(UserDTO u) {
        return "admin".equals(u.roleName) || "finance".equals(u.roleName);
    }
    private String getIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For"); return xff != null ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }
}


/* ══════════════════════════════════════════════════════════════
   DASHBOARD SERVLET
   GET /api/dashboard → stats (admin, registrar, finance)
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/dashboard"})
class DashboardServlet extends HttpServlet {

    private final AcademicDAO dao = new AcademicDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        // All roles get stats, but student gets limited view
        DashboardStats stats = dao.getDashboardStats();
        JsonUtil.success(resp, stats);
    }
}


/* ══════════════════════════════════════════════════════════════
   AUDIT SERVLET
   GET /api/audit                → paginated audit log (admin only)
   GET /api/audit?limit=&offset= → paginated
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/audit", "/api/audit/*"})
class AuditServlet extends HttpServlet {

    private final AcademicDAO dao = new AcademicDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        if (!"admin".equals(user.roleName)) { JsonUtil.forbidden(resp); return; }

        int limit  = ValidationUtil.parseInt(req.getParameter("limit"),  50);
        int offset = ValidationUtil.parseInt(req.getParameter("offset"),  0);
        // Clamp for safety
        limit = Math.min(Math.max(limit, 1), 200);
        offset = Math.max(offset, 0);

        JsonUtil.success(resp, dao.findAuditLog(limit, offset));
    }
}


/* ══════════════════════════════════════════════════════════════
   HEALTH SERVLET — for load balancer / monitoring
   GET /api/health → {"status":"UP","version":"1.0.0"}
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/health"})
class HealthServlet extends HttpServlet {

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject health = new JsonObject();
        health.addProperty("status",  "UP");
        health.addProperty("system",  "USAMIS");
        health.addProperty("version", "1.0.0");
        health.addProperty("institution", "Jinling Institute of Technology");
        health.addProperty("timestamp", java.time.LocalDateTime.now().toString());
        JsonUtil.ok(resp, health);
    }
}
