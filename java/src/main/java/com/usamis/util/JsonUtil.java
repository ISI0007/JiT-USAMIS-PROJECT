package com.usamis.util;

import com.google.gson.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private JsonUtil() {}

    public static String toJson(Object obj) { return GSON.toJson(obj); }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try { return GSON.fromJson(json, clazz); }
        catch (JsonSyntaxException e) { return null; }
    }

    public static JsonElement toJsonElement(Object obj) { return GSON.toJsonTree(obj); }

    public static void writeJson(HttpServletResponse resp, int status, Object payload) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        try (PrintWriter w = resp.getWriter()) { w.print(GSON.toJson(payload)); }
    }

    public static void ok(HttpServletResponse resp, Object data) throws IOException { writeJson(resp, 200, data); }

    public static void success(HttpServletResponse resp, Object data) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", true);
        obj.add("data", GSON.toJsonTree(data));
        writeJson(resp, 200, obj);
    }

    public static void error(HttpServletResponse resp, int status, String msg) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("success", false);
        obj.addProperty("message", msg);
        writeJson(resp, status, obj);
    }

    public static void badRequest(HttpServletResponse resp, String msg)    throws IOException { error(resp, 400, msg); }
    public static void unauthorized(HttpServletResponse resp)               throws IOException { error(resp, 401, "Authentication required."); }
    public static void forbidden(HttpServletResponse resp)                  throws IOException { error(resp, 403, "Access denied — insufficient permissions."); }
    public static void notFound(HttpServletResponse resp, String resource)  throws IOException { error(resp, 404, resource + " not found."); }
    public static void serverError(HttpServletResponse resp, String msg)   throws IOException { error(resp, 500, "Server error: " + msg); }
    public static void conflict(HttpServletResponse resp, String msg)      throws IOException { error(resp, 409, msg); }
}
