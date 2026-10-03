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
   DASHBOARD SERVLET
   GET /api/dashboard → stats (admin, registrar, finance)
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/dashboard"})
public final class DashboardServlet extends HttpServlet {

    private final AcademicDAO dao = new AcademicDAO();

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        UserDTO user = (UserDTO) req.getAttribute("currentUser");
        // All roles get stats, but student gets limited view
        DashboardStats stats = dao.getDashboardStats();
        JsonUtil.success(resp, stats);
    }
}
