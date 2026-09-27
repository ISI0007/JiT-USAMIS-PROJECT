package com.usamis.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilTest {

    @Test
    void validatesIdentifiersAndContactFields() {
        assertTrue(ValidationUtil.isValidStudentId("STU2024001"));
        assertFalse(ValidationUtil.isValidStudentId("2024001"));
        assertTrue(ValidationUtil.isValidCourseCode("CS301"));
        assertFalse(ValidationUtil.isValidCourseCode("cs-301"));
        assertTrue(ValidationUtil.isValidEmail("student@jit.edu.cn"));
        assertFalse(ValidationUtil.isValidEmail("not-an-email"));
        assertTrue(ValidationUtil.isValidPhone("138-0001"));
    }

    @Test
    void validatesRangesAndSafeParsing() {
        assertTrue(ValidationUtil.isValidScore(0.0));
        assertTrue(ValidationUtil.isValidScore(100.0));
        assertFalse(ValidationUtil.isValidScore(100.1));
        assertTrue(ValidationUtil.isValidCredits(3));
        assertFalse(ValidationUtil.isValidCredits(0));
        assertEquals(42, ValidationUtil.parseInt("42", -1));
        assertEquals(-1, ValidationUtil.parseInt("bad", -1));
        assertNull(ValidationUtil.parseDouble("bad"));
    }

    @Test
    void sanitizesMarkupAndDangerousCharacters() {
        assertEquals("hello world", ValidationUtil.sanitize(" <b>hello</b> world "));
        assertEquals("", ValidationUtil.sanitize("<script>alert('x')</script>"));
        assertNull(ValidationUtil.sanitize(null));
    }
}
