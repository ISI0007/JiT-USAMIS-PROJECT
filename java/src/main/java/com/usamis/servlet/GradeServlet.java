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
   GRADE SERVLET
   GET  /api/grades               → all (admin, registrar, lecturer)
   GET  /api/grades?student={id}  → by student
   POST /api/grades               → enter grade (admin, lecturer)
   PUT  /api/grades/{id}          → update grade
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/grades", "/api/grades/*"})
public final class GradeServlet extends HttpServlet {

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
