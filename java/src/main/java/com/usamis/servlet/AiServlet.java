package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.ai.AiService;
import com.usamis.ai.AiService.AiUnavailableException;
import com.usamis.ai.AiService.FeatureBundle;
import com.usamis.dao.StudentDAO;
import com.usamis.model.AiModels.*;
import com.usamis.model.Models.*;
import com.usamis.util.JsonUtil;
import com.usamis.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/*
 * AI INSIGHT SERVLET
 *
 *   GET  /api/ai/status                 - AI service health (admin)
 *   GET  /api/ai/insights               - recent predictions (VIEW_AI)
 *   GET  /api/ai/insights/at-risk       - advisor queue (VIEW_AI)
 *   GET  /api/ai/student/{id}           - latest insight for a student
 *   POST /api/ai/predict/{id}           - run a fresh prediction (VIEW_AI)
 *   GET  /api/ai/student/{id}/features  - the feature vector used (transparency)
 *
 * WHY a single servlet: the whole AI surface shares auth + error mapping; splitting
 * it into six servlets would duplicate the token/permission guard six times.
 */
@WebServlet(urlPatterns = {"/api/ai/status", "/api/ai/me", "/api/ai/insights", "/api/ai/insights/*",
        "/api/ai/student/*", "/api/ai/predict/*"})
public class AiServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AiServlet.class);

    private final AiService  aiService  = new AiService();
    private final StudentDAO studentDAO = new StudentDAO();
    private final com.usamis.dao.AcademicDAO academicDAO = new com.usamis.dao.AcademicDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String path  = req.getServletPath();
        String info  = req.getPathInfo();

        // GET /api/ai/status
        if ("/api/ai/status".equals(path)) {
            if (!isAdmin(user)) { JsonUtil.forbidden(resp); return; }
            JsonUtil.success(resp, aiService.status());
            return;
        }

        // GET /api/ai/me — a student's OWN latest insight, resolved server-side
        if ("/api/ai/me".equals(path)) {
            if (!"student".equalsIgnoreCase(user.roleName)) { JsonUtil.forbidden(resp); return; }
            var sidOpt = aiService.studentIdForUser(user.id);
            if (sidOpt.isEmpty()) { JsonUtil.notFound(resp, "Student record"); return; }
            aiService.historyForStudent(sidOpt.get(), 1).stream().findFirst()
                .ifPresentOrElse(
                    p -> { try { JsonUtil.success(resp, p); } catch (IOException e) { throw new RuntimeException(e); } },
                    () -> { try { JsonUtil.success(resp, null); } catch (IOException e) { throw new RuntimeException(e); } });
            return;
        }

        // GET /api/ai/insights/at-risk
        if ("/api/ai/insights".equals(path) && "/at-risk".equals(info)) {
            if (!canViewAi(user)) { deny(resp, user, req); return; }
            int limit = clamp(ValidationUtil.parseInt(req.getParameter("limit"), 50), 1, 200);
            JsonUtil.success(resp, aiService.atRiskStudents(limit));
            return;
        }

        // GET /api/ai/insights
        if ("/api/ai/insights".equals(path)) {
            if (!canViewAi(user)) { deny(resp, user, req); return; }
            int limit = clamp(ValidationUtil.parseInt(req.getParameter("limit"), 50), 1, 200);
            JsonUtil.success(resp, aiService.recentPredictions(limit));
            return;
        }

        // GET /api/ai/student/{id}  and  /api/ai/student/{id}/features
        if ("/api/ai/student".equals(path) && info != null) {
            String[] parts = info.substring(1).split("/");
            int studentId = ValidationUtil.parseInt(parts[0], -1);
            if (studentId < 0) { JsonUtil.badRequest(resp, "Invalid student id"); return; }
            if (!canViewStudent(user, studentId)) { deny(resp, user, req); return; }

            if (parts.length > 1 && "features".equals(parts[1])) {
                Optional<FeatureBundle> fb = aiService.buildFeatures(studentId);
                if (fb.isEmpty()) { JsonUtil.notFound(resp, "Student"); return; }
                JsonUtil.success(resp, fb.get().toJson());
                return;
            }
            aiService.historyForStudent(studentId, 10).stream().findFirst()
                .ifPresentOrElse(
                    p -> { try { JsonUtil.success(resp, p); } catch (IOException e) { throw new RuntimeException(e); } },
                    () -> { try { JsonUtil.success(resp, null); } catch (IOException e) { throw new RuntimeException(e); } });
            return;
        }

        JsonUtil.notFound(resp, "AI endpoint");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String path  = req.getServletPath();
        String info  = req.getPathInfo();

        // POST /api/ai/predict/{studentId}
        if ("/api/ai/predict".equals(path) && info != null && info.length() > 1) {
            int studentId = ValidationUtil.parseInt(info.substring(1), -1);
            if (studentId < 0) { JsonUtil.badRequest(resp, "Invalid student id"); return; }
            if (!canViewStudent(user, studentId)) { deny(resp, user, req); return; }

            try {
                InsightDTO dto = aiService.predictPerformance(studentId, user.id);
                if (dto == null) { JsonUtil.notFound(resp, "Student"); return; }
                academicDAO.log(user.id, user.username, user.roleName, "CREATE", "AI",
                    "Ran performance prediction for student #" + studentId +
                    " (" + dto.modelName + " " + dto.modelVersion + ")", getIp(req), null);
                JsonUtil.success(resp, dto);
            } catch (AiUnavailableException e) {
                log.warn("AI unavailable: {}", e.getMessage());
                JsonUtil.error(resp, 503, "AI service unavailable: " + e.getMessage());
            } catch (Exception e) {
                log.error("AI prediction failed", e);
                JsonUtil.serverError(resp, e.getMessage());
            }
            return;
        }

        JsonUtil.notFound(resp, "AI endpoint");
    }

    // --- authorization --------------------------------------
    /**
     * AI insight is a staff analytics surface: admin/registrar/lecturer/finance.
     * Students are deliberately excluded from the aggregate views and may only
     * see their OWN student record (canViewStudent below).
     */
    private boolean canViewAi(UserDTO u) {
        if (u == null || u.roleName == null) return false;
        return switch (u.roleName.toLowerCase()) {
            case "admin", "registrar", "lecturer", "finance" -> true;
            default -> false;
        };
    }

    private boolean isAdmin(UserDTO u) {
        return u != null && "admin".equalsIgnoreCase(u.roleName);
    }

    /** Students may only read the student record linked to their user id. */
    private boolean canViewStudent(UserDTO u, int studentId) {
        if (canViewAi(u)) return true;
        if (u == null || !"student".equalsIgnoreCase(u.roleName)) return false;
        // students.user_id -> students.id mapping
        Optional<Student> s = studentDAO.findById(studentId);
        return s.isPresent() && s.get().userId != null && s.get().userId.equals(u.id);
    }

    private void deny(HttpServletResponse resp, UserDTO user, HttpServletRequest req) throws IOException {
        if (user != null) {
            academicDAO.log(user.id, user.username, user.roleName, "ACCESS_DENIED", "AI",
                "Permission denied for AI insight", getIp(req), null);
        }
        JsonUtil.forbidden(resp);
    }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private String getIp(HttpServletRequest r) {
        String xff = r.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : r.getRemoteAddr();
    }
}