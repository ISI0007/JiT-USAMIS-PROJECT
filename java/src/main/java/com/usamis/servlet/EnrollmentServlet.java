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
   ENROLLMENT SERVLET
   GET  /api/enrollments              → all enrollments
   GET  /api/enrollments?student={id} → by student
   POST /api/enrollments              → enroll (admin, registrar)
   DELETE /api/enrollments/{id}       → drop
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/enrollments", "/api/enrollments/*"})
public final class EnrollmentServlet extends HttpServlet {

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
