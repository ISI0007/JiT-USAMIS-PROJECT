package com.usamis.servlet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.usamis.model.Models.UserDTO;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/permissions        — the full role x permission matrix (admin).
 * GET /api/permissions/mine   — the CURRENT user's own permissions (any role).
 *
 * WHY: the login page / profile should tell every user exactly what they are
 * allowed to do, sourced from the SAME tables the server enforces
 * (role_permissions). The old UI carried a hand-written matrix that drifted from
 * the database; this endpoint makes the displayed matrix the real one.
 *
 * Security: /mine is self-scoped (always the caller's own role). The full
 * matrix is admin-only because it exposes the whole authorization model.
 */
@WebServlet(urlPatterns = {"/api/permissions", "/api/permissions/mine"})
public class PermissionServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(PermissionServlet.class);

    /** Human-friendly labels for each permission, so the UI reads well. */
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("MANAGE_USERS",       "Manage Users"),
        Map.entry("MANAGE_STUDENTS",    "Manage Students"),
        Map.entry("MANAGE_COURSES",     "Manage Courses"),
        Map.entry("MANAGE_ENROLLMENT",  "Manage Enrollment"),
        Map.entry("ENTER_GRADES",       "Enter Grades"),
        Map.entry("VIEW_GRADES",        "View Grades"),
        Map.entry("MANAGE_FEES",        "Manage Fees"),
        Map.entry("VIEW_FEES",          "View Fees"),
        Map.entry("VIEW_REPORTS",       "Generate Reports"),
        Map.entry("VIEW_AUDIT_LOG",     "View Audit Log"),
        Map.entry("VIEW_AI",            "View AI Insights"),
        Map.entry("MANAGE_AI",          "Manage / Train AI"),
        Map.entry("VIEW_OWN_PROFILE",   "View Own Profile")
    );

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        String path = req.getServletPath();
        if (path == null || path.isEmpty()) {
            String pi = req.getPathInfo();
            path = (pi != null) ? pi : req.getRequestURI();
        }

        if (path.endsWith("/mine")) {
            if (user == null) { JsonUtil.unauthorized(resp); return; }
            JsonUtil.success(resp, myPermissions(user));
            return;
        }

        // Full matrix — admin only.
        if (user == null || !"admin".equalsIgnoreCase(user.roleName)) {
            JsonUtil.forbidden(resp);
            return;
        }
        JsonUtil.success(resp, fullMatrix());
    }

    /** { role, roleLabel, permissions:[{name,label,module}] } for the caller. */
    private JsonObject myPermissions(UserDTO user) {
        JsonObject out = new JsonObject();
        out.addProperty("role", user.roleName);
        out.addProperty("roleId", user.roleId);
        JsonArray perms = new JsonArray();
        String sql = "SELECT p.name, p.module FROM role_permissions rp " +
                     "JOIN permissions p ON p.id = rp.permission_id " +
                     "WHERE rp.role_id = ? ORDER BY p.module, p.name";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.roleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject p = new JsonObject();
                    String name = rs.getString("name");
                    p.addProperty("name", name);
                    p.addProperty("label", LABELS.getOrDefault(name, name));
                    p.addProperty("module", rs.getString("module"));
                    perms.add(p);
                }
            }
        } catch (SQLException e) {
            log.error("myPermissions for role {}", user.roleId, e);
        }
        out.add("permissions", perms);
        return out;
    }

    /**
     * { roles:[{name,label,id}], permissions:[{name,label,module}],
     *   grants:{ "<roleName>": ["PERM", ...] } }
     * A flat, render-ready shape: the UI draws one column per role.
     */
    private JsonObject fullMatrix() {
        JsonObject out = new JsonObject();

        // Roles
        JsonArray roles = new JsonArray();
        List<String> roleNames = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM roles ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonObject r = new JsonObject();
                String name = rs.getString("name");
                r.addProperty("id", rs.getInt("id"));
                r.addProperty("name", name);
                r.addProperty("label", capitalize(name));
                roles.add(r);
                roleNames.add(name);
            }
        } catch (SQLException e) {
            log.error("fullMatrix roles", e);
        }
        out.add("roles", roles);

        // Permissions (definitions)
        JsonArray perms = new JsonArray();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT name, module FROM permissions ORDER BY module, name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonObject p = new JsonObject();
                String name = rs.getString("name");
                p.addProperty("name", name);
                p.addProperty("label", LABELS.getOrDefault(name, name));
                p.addProperty("module", rs.getString("module"));
                perms.add(p);
            }
        } catch (SQLException e) {
            log.error("fullMatrix permissions", e);
        }
        out.add("permissions", perms);

        // Grants: roleName -> [permissionName, ...]
        JsonObject grants = new JsonObject();
        for (String rn : roleNames) grants.add(rn, new JsonArray());
        String sql = "SELECT r.name AS role, p.name AS perm FROM role_permissions rp " +
                     "JOIN roles r ON r.id = rp.role_id " +
                     "JOIN permissions p ON p.id = rp.permission_id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                grants.getAsJsonArray(rs.getString("role")).add(rs.getString("perm"));
            }
        } catch (SQLException e) {
            log.error("fullMatrix grants", e);
        }
        out.add("grants", grants);
        return out;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
