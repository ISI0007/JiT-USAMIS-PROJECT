package com.usamis.filter;

import com.usamis.model.Models.UserDTO;
import com.usamis.util.JsonUtil;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 * WHY a Filter instead of per-servlet checks:
 * Centralising security in a filter means:
 *  1. Impossible to forget the check on a new servlet
 *  2. Single point to audit/change auth logic
 *  3. Runs before any business logic — fail-fast
 *
 * Mapped to /api/* — all JSON API endpoints require authentication.
 * Static files and /api/auth/login are whitelisted.
 */
@WebFilter(urlPatterns = {"/api/*"})
public class AuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    /** Paths that don't require a valid session */
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/auth/login",
        "/api/auth/logout",
        "/api/auth/register",
        "/api/lookups/departments",
        "/api/lookups/programs",
        "/api/health"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // CORS headers for development (restrict in production to your actual origin)
        response.setHeader("Access-Control-Allow-Origin",  request.getHeader("Origin"));
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Requested-With");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // NOTE: for prefix mappings like "/api/*" the container sets getServletPath()
        // to "" and puts the matched remainder in getPathInfo(). Relying on
        // getServletPath() alone would make the whitelist and routing check read an
        // empty string. Reconstruct the full request path from both parts.
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            String pi = request.getPathInfo();
            path = (pi != null) ? pi : request.getRequestURI();
        }

        // Whitelist public endpoints
        if (PUBLIC_PATHS.contains(path)) {
            chain.doFilter(req, res);
            return;
        }

        // Session check
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            log.warn("Unauthenticated access attempt: {} {}", request.getMethod(), path);
            JsonUtil.unauthorized(response);
            return;
        }

        // Attach user to request so servlets can access it without re-querying session
        UserDTO user = (UserDTO) session.getAttribute("user");
        request.setAttribute("currentUser", user);

        chain.doFilter(req, res);
    }

    @Override public void init(FilterConfig cfg) {}
    @Override public void destroy() {}
}
