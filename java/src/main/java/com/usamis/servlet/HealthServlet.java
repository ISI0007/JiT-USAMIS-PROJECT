package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.util.JsonUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(urlPatterns = "/api/health")
public class HealthServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject health = new JsonObject();
        health.addProperty("status", "UP");
        health.addProperty("system", "USAMIS");
        health.addProperty("version", "1.0.0");
        health.addProperty("institution", "Jinling Institute of Technology");
        health.addProperty("timestamp", java.time.LocalDateTime.now().toString());
        JsonUtil.ok(resp, health);
    }
}
