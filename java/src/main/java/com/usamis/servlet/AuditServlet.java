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
   AUDIT SERVLET
   GET /api/audit                → paginated audit log (admin only)
   GET /api/audit?limit=&offset= → paginated
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/audit", "/api/audit/*"})
public final class AuditServlet extends HttpServlet {

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
