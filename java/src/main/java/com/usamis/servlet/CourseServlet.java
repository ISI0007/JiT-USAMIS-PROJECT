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
public final class CourseServlet extends HttpServlet {

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
