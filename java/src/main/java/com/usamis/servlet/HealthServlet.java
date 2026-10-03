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
   HEALTH SERVLET — for load balancer / monitoring
   GET /api/health → {"status":"UP","version":"1.0.0"}
══════════════════════════════════════════════════════════════ */
@WebServlet(urlPatterns = {"/api/health"})
public final class HealthServlet extends HttpServlet {

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
