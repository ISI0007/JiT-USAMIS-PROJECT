package com.usamis.util;

import java.util.regex.Pattern;

/**
 * WHY server-side validation: Frontend JS validation can be bypassed
 * (curl, Postman, browser devtools). All constraints must be enforced
 * on the server, backed by DB constraints as a final safety net.
 */
public final class ValidationUtil {

    private static final Pattern EMAIL   = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern STUDENT_ID = Pattern.compile("^STU\\d{7}$");
    private static final Pattern COURSE_CODE = Pattern.compile("^[A-Z]{2,6}\\d{3,4}$");
    private static final Pattern PHONE   = Pattern.compile("^[\\d\\s\\-+()]{7,20}$");

    private ValidationUtil() {}

    public static boolean isBlank(String s)     { return s == null || s.isBlank(); }
    public static boolean notBlank(String s)    { return !isBlank(s); }
    public static boolean isValidEmail(String e){ return notBlank(e) && EMAIL.matcher(e.trim()).matches(); }
    public static boolean isValidStudentId(String id) { return notBlank(id) && STUDENT_ID.matcher(id.trim()).matches(); }
    public static boolean isValidCourseCode(String c) { return notBlank(c) && COURSE_CODE.matcher(c.trim()).matches(); }
    public static boolean isValidPhone(String p){ return isBlank(p) || PHONE.matcher(p.trim()).matches(); }

    public static boolean isValidScore(Double score) {
        return score != null && score >= 0.0 && score <= 100.0;
    }

    public static boolean isValidCredits(Integer credits) {
        return credits != null && credits >= 1 && credits <= 8;
    }

    /** Strip HTML tags to prevent XSS — never trust user input */
    public static String sanitize(String input) {
        if (input == null) return null;
        return input.trim()
                    .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                    .replaceAll("(?is)<style[^>]*>.*?</style>", "")
                    .replaceAll("<[^>]*>", "")        // remove HTML tags
                    .replaceAll("[<>\"'%;()&+]", ""); // remove dangerous chars
    }

    /** Return safe integer or default */
    public static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    /** Return safe double or null */
    public static Double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }
}
