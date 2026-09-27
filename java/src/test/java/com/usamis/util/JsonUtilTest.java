package com.usamis.util;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void roundTripsJavaTimeValuesWithoutReflectionErrors() {
        JsonObject payload = new JsonObject();
        payload.addProperty("date", LocalDate.of(2024, 5, 12).toString());
        payload.addProperty("timestamp", LocalDateTime.of(2024, 5, 12, 10, 30).toString());

        assertEquals("2024-05-12", payload.get("date").getAsString());
        assertEquals("2024-05-12T10:30", payload.get("timestamp").getAsString());
        assertNotNull(JsonUtil.fromJson(JsonUtil.toJson(payload), JsonObject.class));
    }

    @Test
    void invalidJsonReturnsNull() {
        assertNull(JsonUtil.fromJson("{invalid", JsonObject.class));
    }
}
