package com.usamis.servlet;

import com.google.gson.JsonObject;
import com.usamis.dao.AcademicDAO;
import com.usamis.service.AuthService;
import com.usamis.model.Models.User;
import com.usamis.model.Models.UserDTO;
import com.usamis.util.JsonUtil;
import com.usamis.util.ValidationUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * POST /api/auth/login   — authenticate and create session
 * POST /api/auth/logout  — invalidate session
 * GET  /api/auth/me      — return current session user
 *
 * WHY session instead of JWT here:
 * Server-side sessions are simpler for a Servlet app and allow
 * instant revocation (block a user → their next request fails).
 * JWT would require a token blacklist to achieve the same.
 */
@WebServlet(urlPatterns = {"/api/auth/login", "/api/auth/logout", "/api/auth/me"})
public class LoginServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(LoginServlet.class);
    private static final int SESSION_TIMEOUT = 60 * 60; // 1 hour

    private final AuthService authService = new AuthService();
    private final AcademicDAO academicDAO = new AcademicDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getServletPath();

        if ("/api/auth/login".equals(path)) {
            handleLogin(req, resp);
        } else if ("/api/auth/logout".equals(path)) {
            handleLogout(req, resp);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // GET /api/auth/me — return current user from session
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            JsonUtil.unauthorized(resp);
            return;
        }
        JsonUtil.success(resp, session.getAttribute("user"));
    }

    // ─── LOGIN ──────────────────────────────────────────────
    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Parse JSON body
        String body;
        try (var reader = req.getReader()) {
            body = reader.lines().reduce("", String::concat);
        }

        JsonObject json = JsonUtil.fromJson(body, JsonObject.class);
        if (json == null) { JsonUtil.badRequest(resp, "Invalid JSON body"); return; }

        String username = json.has("username") ? json.get("username").getAsString().trim() : "";
        String password = json.has("password") ? json.get("password").getAsString() : "";

        // Basic input validation
        if (ValidationUtil.isBlank(username) || ValidationUtil.isBlank(password)) {
            JsonUtil.badRequest(resp, "Username and password are required.");
            return;
        }

        // Rate-limiting check (simple in-memory; use Redis in production)
        String ip = getClientIp(req);
        if (isRateLimited(ip)) {
            log.warn("Rate limit hit for IP={}", ip);
            JsonUtil.error(resp, 429, "Too many login attempts. Please wait and try again.");
            return;
        }

        // Authenticate via DAO (bcrypt verify inside)
        Optional<User> result = authService.authenticate(username, password);

        if (result.isEmpty()) {
            recordFailedAttempt(ip);
            log.warn("Failed login: username={} ip={}", username, ip);
            // Audit failed attempt
            academicDAO.log(0, username, "unknown", "LOGIN_FAILED", "Auth",
                "Failed login attempt for username: " + username, ip, req.getHeader("User-Agent"));
            // Generic error — don't reveal whether username or password was wrong
            JsonUtil.error(resp, 401, "Invalid credentials. Please check your username and password.");
            return;
        }

        User user = result.get();
        UserDTO dto = user.toDTO();

        // Create server-side session
        HttpSession session = req.getSession(true);
        // Rotate the identifier after authentication to prevent session fixation.
        req.changeSessionId();
        session.setAttribute("user", dto);
        session.setAttribute("roleId", user.roleId);
        session.setMaxInactiveInterval(SESSION_TIMEOUT);

        // Audit successful login
        academicDAO.log(dto.id, dto.username, dto.roleName, "LOGIN", "Auth",
            "Successful login from " + ip, ip, req.getHeader("User-Agent"));

        log.info("Login success: user={} role={} ip={}", dto.username, dto.roleName, ip);
        clearFailedAttempts(ip);

        // Return user data (no password hash)
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", "Login successful");
        response.add("user", JsonUtil.toJsonElement(dto));
        JsonUtil.ok(resp, response);
    }

    // ─── LOGOUT ─────────────────────────────────────────────
    private void handleLogout(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            UserDTO user = (UserDTO) session.getAttribute("user");
            if (user != null) {
                academicDAO.log(user.id, user.username, user.roleName, "LOGOUT", "Auth",
                    "User logged out", getClientIp(req), req.getHeader("User-Agent"));
                log.info("Logout: user={}", user.username);
            }
            session.invalidate();
        }
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", "Logged out successfully");
        JsonUtil.ok(resp, response);
    }

    // ─── RATE LIMITING (simple in-memory) ───────────────────
    // In production replace with Redis-based sliding window
    private static final java.util.concurrent.ConcurrentHashMap<String, int[]> ATTEMPTS
        = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;

    private boolean isRateLimited(String ip) {
        int[] a = ATTEMPTS.get(ip);
        return a != null && a[0] >= MAX_ATTEMPTS;
    }

    private void recordFailedAttempt(String ip) {
        ATTEMPTS.merge(ip, new int[]{1}, (old, v) -> { old[0]++; return old; });
    }

    private void clearFailedAttempts(String ip) {
        ATTEMPTS.remove(ip);
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
