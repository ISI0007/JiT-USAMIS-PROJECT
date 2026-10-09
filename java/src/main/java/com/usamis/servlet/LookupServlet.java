package com.usamis.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.usamis.util.DatabaseConnection;
import com.usamis.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * GET /api/lookups/departments
 * GET /api/lookups/programs[?departmentId=N]
 *
 * Read-only reference data for forms (notably student self-registration, which
 * must render before the caller has a session). Returns the same
 * {success, data:[{dbId, code, name, ...}]} envelope the SPA expects.
 *
 * WHY a dedicated lookup servlet: registration is public, so these two reads
 * must be reachable without a session — but they expose only non-sensitive
 * catalog data (department/program names), never personal records.
 */
@WebServlet(urlPatterns = {"/api/lookups/departments", "/api/lookups/programs"})
public class LookupServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LookupServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getServletPath();
        if (path == null || path.isEmpty()) {
            String pi = req.getPathInfo();
            path = (pi != null) ? pi : req.getRequestURI();
        }

        if (path.endsWith("/departments")) {
            JsonUtil.success(resp, departments());
        } else if (path.endsWith("/programs")) {
            int deptId = com.usamis.util.ValidationUtil.parseInt(req.getParameter("departmentId"), -1);
            JsonUtil.success(resp, programs(deptId));
        } else {
            JsonUtil.notFound(resp, "Lookup");
        }
    }

    private JsonArray departments() {
        JsonArray out = new JsonArray();
        String sql = "SELECT id, code, name FROM departments ORDER BY name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonObject o = new JsonObject();
                o.addProperty("dbId", rs.getInt("id"));
                o.addProperty("code", rs.getString("code"));
                o.addProperty("name", rs.getString("name"));
                out.add(o);
            }
        } catch (SQLException e) {
            log.error("lookup departments", e);
        }
        return out;
    }

    private JsonArray programs(int deptId) {
        JsonArray out = new JsonArray();
        String sql = "SELECT id, department_id, name, duration_years FROM programs " +
                     (deptId > 0 ? "WHERE department_id = ? " : "") + "ORDER BY name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (deptId > 0) ps.setInt(1, deptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("dbId", rs.getInt("id"));
                    o.addProperty("departmentId", rs.getInt("department_id"));
                    o.addProperty("name", rs.getString("name"));
                    o.addProperty("durationYears", rs.getInt("duration_years"));
                    out.add(o);
                }
            }
        } catch (SQLException e) {
            log.error("lookup programs", e);
        }
        return out;
    }
}
