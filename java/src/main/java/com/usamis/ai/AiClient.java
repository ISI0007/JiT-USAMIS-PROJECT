package com.usamis.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * HTTP client for the Python AI service.
 *
 * WHY a thin HttpURLConnection client instead of a library:
 *  - zero new dependencies (the WAR stays small, matching the project style)
 *  - the only call sites are in this package, so an extra abstraction is noise
 *
 * SECURITY: the shared service token is read from ai.properties (not committed)
 * and sent in the X-AI-Service-Token header. The AI service is expected to be on
 * a loopback/private address; this client never proxies a public request through.
 */
public final class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);
    private static final Gson GSON = new Gson();

    private static final String BASE_URL;
    private static final String TOKEN;
    private static final int TIMEOUT_MS;

    static {
        Properties p = new Properties();
        String base = "http://127.0.0.1:8099";
        String tok  = "";
        int timeout = 4000;
        try (InputStream is = AiClient.class.getClassLoader().getResourceAsStream("ai.properties")) {
            if (is != null) {
                p.load(is);
                base = p.getProperty("ai.base.url", base);
                tok  = p.getProperty("ai.service.token", tok);
                timeout = Integer.parseInt(p.getProperty("ai.timeout.ms", String.valueOf(timeout)));
            } else {
                log.warn("ai.properties not found on classpath - AI calls disabled until configured");
            }
        } catch (Exception e) {
            log.warn("Could not load ai.properties: {}", e.getMessage());
        }
        BASE_URL = base;
        TOKEN = tok;
        TIMEOUT_MS = timeout;
    }

    private AiClient() {}

    public static boolean isConfigured() {
        return TOKEN != null && !TOKEN.isBlank();
    }

    /** Result wrapper so callers never deal with exceptions or nulls. */
    public static final class Result {
        public final boolean ok;
        public final int     status;
        public final JsonObject body;
        public final String  error;
        private Result(boolean ok, int status, JsonObject body, String error) {
            this.ok = ok; this.status = status; this.body = body; this.error = error;
        }
        static Result ok(int status, JsonObject body) { return new Result(true, status, body, null); }
        static Result fail(int status, String error)  { return new Result(false, status, null, error); }
    }

    /** GET /health - public, no token required. */
    public static Result health() {
        return call("GET", "/health", null, false);
    }

    /** POST /api/v1/predict/performance */
    public static Result predictPerformance(JsonObject payload) {
        return call("POST", "/api/v1/predict/performance", payload, true);
    }

    /** POST /api/v1/forecast/enrollment */
    public static Result forecastEnrollment(JsonObject payload) {
        return call("POST", "/api/v1/forecast/enrollment", payload, true);
    }

    /** POST /api/v1/recommend/courses */
    public static Result recommendCourses(JsonObject payload) {
        return call("POST", "/api/v1/recommend/courses", payload, true);
    }

    private static Result call(String method, String path, JsonObject payload, boolean auth) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            if (auth) {
                if (!isConfigured()) return Result.fail(503, "AI service token not configured on the server");
                conn.setRequestProperty("X-AI-Service-Token", TOKEN);
            }
            if (payload != null) {
                conn.setDoOutput(true);
                byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
            }
            int status = conn.getResponseCode();
            InputStream stream = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject obj = null;
            try { obj = GSON.fromJson(text, JsonObject.class); } catch (Exception ignored) {}
            if (status >= 200 && status < 300) return Result.ok(status, obj);
            String msg = (obj != null && obj.has("detail")) ? obj.get("detail").getAsString() : ("AI service returned " + status);
            return Result.fail(status, msg);
        } catch (Exception e) {
            log.warn("AI service call failed: {} {}", method, path);
            return Result.fail(-1, "AI service unreachable: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}