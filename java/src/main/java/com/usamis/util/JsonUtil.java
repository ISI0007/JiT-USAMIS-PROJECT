package com.usamis.util;

import com.google.gson.*;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class JsonUtil {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .setPrettyPrinting()
            .serializeNulls()
            // Gson cannot reflect over java.time types under the Java module system
            // (java.base does not "opens java.time"), which throws
            // InaccessibleObjectException when serialising LocalDateTime/LocalDate.
            // Explicit adapters avoid reflection entirely.
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .create();

    /** Serialises java.time.LocalDateTime as "yyyy-MM-dd HH:mm:ss" or null. */
    private static final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) out.nullValue(); else out.value(DT_FMT.format(value));
        }
        @Override public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return LocalDateTime.parse(in.nextString(), DT_FMT);
        }
    }

    /** Serialises java.time.LocalDate as "yyyy-MM-dd" or null. */
    private static final class LocalDateAdapter extends TypeAdapter<LocalDate> {
        @Override public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) out.nullValue(); else out.value(value.toString());
        }
        @Override public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            return LocalDate.parse(in.nextString());
        }
    }

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
