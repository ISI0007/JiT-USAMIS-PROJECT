package com.usamis.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.usamis.dao.AcademicDAO;
// AiPredictionDAO is in the same package (com.usamis.ai).
import com.usamis.dao.StudentDAO;
import com.usamis.model.AiModels.*;
import com.usamis.model.Models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bridges the USAMIS domain data and the AI service.
 *
 * Responsibilities:
 *  1. Build the MLP feature vector from EXISTING tables (students, enrollments,
 *     grades, attendance) - the AI service never sees the database.
 *  2. Call the AI service, persist the prediction, and translate the response
 *     into a UI-friendly InsightDTO.
 *  3. Fail gracefully: if the AI service is down, callers get a clear error,
 *     never a stack trace or a silent wrong number.
 */
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final StudentDAO      studentDAO    = new StudentDAO();
    private final AcademicDAO     academicDAO   = new AcademicDAO();
    private final AiPredictionDAO predictionDAO = new AiPredictionDAO();

    /**
     * Feature snapshot for one student, derived entirely from existing tables.
     * Attendance may be absent in seed data - a neutral 0.85 prior is used and
     * the value is tagged so the UI can show a confidence caveat.
     */
    public static class FeatureBundle {
        public double priorGpa;
        public double attendanceRate;
        public double assignmentAvg;
        public double quizAvg;
        public double creditsAttempted;
        public double numPriorCourses;
        public double failureCount;
        public double yearOfStudy;
        public boolean attendanceEstimated;

        public JsonObject toJson() {
            JsonObject f = new JsonObject();
            f.addProperty("prior_gpa", priorGpa);
            f.addProperty("attendance_rate", attendanceRate);
            f.addProperty("assignment_avg", assignmentAvg);
            f.addProperty("quiz_avg", quizAvg);
            f.addProperty("credits_attempted", creditsAttempted);
            f.addProperty("num_prior_courses", numPriorCourses);
            f.addProperty("failure_count", failureCount);
            f.addProperty("year_of_study", yearOfStudy);
            return f;
        }
    }

    /** Build features for a student from the live database. */
    public Optional<FeatureBundle> buildFeatures(int studentId) {
        Optional<Student> sOpt = studentDAO.findById(studentId);
        if (sOpt.isEmpty()) return Optional.empty();
        Student s = sOpt.get();

        List<Grade> grades = academicDAO.findGradesByStudent(studentId);
        List<Enrollment> enrolls = academicDAO.findEnrollmentsByStudent(studentId);

        FeatureBundle f = new FeatureBundle();
        f.yearOfStudy = s.yearOfStudy;
        f.priorGpa = (s.gpa != null) ? s.gpa : 0.0;

        // Coursework signals from grades where present.
        List<Double> scores = new ArrayList<>();
        int failures = 0;
        for (Grade g : grades) {
            if (g.score != null) {
                scores.add(g.score);
                if (g.score < 60.0) failures++;
            }
        }
        double avg = scores.isEmpty() ? 65.0 : scores.stream().mapToDouble(Double::doubleValue).average().orElse(65.0);
        f.assignmentAvg = avg;
        f.quizAvg = avg;
        f.failureCount = failures;
        f.numPriorCourses = grades.size();
        double credits = 0;
        for (Enrollment e : enrolls) credits += 3; // credits unknown here; 3 is the modal course weight
        f.creditsAttempted = credits;

        // Attendance: derive if rows exist, else neutral estimate.
        double rate = attendanceRateFor(studentId);
        if (rate < 0) { f.attendanceRate = 0.85; f.attendanceEstimated = true; }
        else { f.attendanceRate = rate; f.attendanceEstimated = false; }

        return Optional.of(f);
    }

    /** Attendance ratio from the attendance table; -1 if no rows. */
    private double attendanceRateFor(int studentId) {
        String sql = "SELECT AVG(CASE WHEN a.present THEN 1.0 ELSE 0.0 END) " +
            "FROM attendance a JOIN enrollments e ON a.enrollment_id = e.id " +
            "WHERE e.student_id = ?";
        try (java.sql.Connection conn = com.usamis.util.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double v = rs.getDouble(1);
                    if (rs.wasNull()) return -1;
                    return v;
                }
            }
        } catch (Exception e) {
            log.warn("attendance lookup failed for student {}: {}", studentId, e.getMessage());
        }
        return -1;
    }

    /**
     * Predict performance for one student, persist, and return a UI DTO.
     * Throws AiUnavailableException when the model service cannot be reached,
     * so the caller returns a real 503. */
    public InsightDTO predictPerformance(int studentId, Integer requestedBy) throws AiUnavailableException {
        Optional<FeatureBundle> fb = buildFeatures(studentId);
        if (fb.isEmpty()) return null;

        JsonObject payload = new JsonObject();
        payload.addProperty("student_id", studentId);
        payload.add("features", fb.get().toJson());

        AiClient.Result r = AiClient.predictPerformance(payload);
        if (!r.ok) throw new AiUnavailableException(r.error);
        JsonObject body = r.body;
        if (body == null) throw new AiUnavailableException("empty AI response");

        InsightDTO dto = new InsightDTO();
        dto.studentId       = studentId;
        dto.predictedScore  = getD(body, "predicted_score", 0);
        dto.riskProbability = getD(body, "risk_probability", 0);
        dto.atRisk          = body.has("at_risk") && body.get("at_risk").getAsBoolean();
        dto.modelName       = getS(body, "model_name", "mlp_performance");
        dto.modelVersion    = getS(body, "model_version", "unknown");
        dto.trainedOn       = getS(body, "trained_on", "synthetic");
        dto.topFactors      = parseFactors(body);
        dto.advice          = advice(dto);

        Optional<Student> s = studentDAO.findById(studentId);
        if (s.isPresent()) { dto.studentNo = s.get().studentId; dto.studentName = s.get().fullName(); }

        // Persist (best-effort: a failed save must not break the response).
        try {
            predictionDAO.save(studentId, dto.modelName, dto.modelVersion, "PERFORMANCE",
                dto.predictedScore, dto.riskProbability, dto.atRisk,
                com.usamis.util.JsonUtil.toJson(dto), requestedBy);
        } catch (Exception e) {
            log.warn("Could not persist AI prediction: {}", e.getMessage());
        }
        return dto;
    }

    /** Resolve students.id from a user id (students.user_id). Empty if none. */
    public java.util.Optional<Integer> studentIdForUser(int userId) {
        String sql = "SELECT id FROM students WHERE user_id = ? LIMIT 1";
        try (java.sql.Connection conn = com.usamis.util.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return java.util.Optional.of(rs.getInt(1));
            }
        } catch (Exception e) {
            log.warn("studentIdForUser({}) failed: {}", userId, e.getMessage());
        }
        return java.util.Optional.empty();
    }

    /** Recent predictions for a student (self-scoped). */
    public List<Prediction> historyForStudent(int studentId, int limit) {
        return predictionDAO.findByStudent(studentId, limit);
    }

    /** Recent predictions across all students (staff/admin). */
    public List<Prediction> recentPredictions(int limit) {
        return predictionDAO.findRecent(limit);
    }

    /** Advisor queue: students whose latest prediction flagged at-risk. */
    public List<Prediction> atRiskStudents(int limit) {
        return predictionDAO.findAtRisk(limit);
    }

    /** AI service health snapshot for the admin panel. */
    public ServiceStatus status() {
        ServiceStatus st = new ServiceStatus();
        AiClient.Result r = AiClient.health();
        st.reachable = r.ok;
        if (r.ok && r.body != null) {
            st.status = getS(r.body, "status", "UP");
            st.version = getS(r.body, "version", "");
            st.tokenConfigured = r.body.has("token_configured") && r.body.get("token_configured").getAsBoolean();
            st.models = new java.util.LinkedHashMap<>();
            if (r.body.has("models") && r.body.get("models").isJsonObject()) {
                for (var e : r.body.getAsJsonObject("models").entrySet()) {
                    st.models.put(e.getKey(), e.getValue().getAsBoolean());
                }
            }
            st.message = "AI service reachable";
        } else {
            st.message = (r.error != null) ? r.error : "AI service unreachable";
            st.tokenConfigured = AiClient.isConfigured();
        }
        return st;
    }

    // --- helpers -------------------------------------------
    private static List<Factor> parseFactors(JsonObject body) {
        List<Factor> out = new ArrayList<>();
        if (body.has("top_factors") && body.get("top_factors").isJsonArray()) {
            JsonArray arr = body.getAsJsonArray("top_factors");
            for (var el : arr) {
                JsonObject o = el.getAsJsonObject();
                Factor f = new Factor();
                f.factor    = getS(o, "factor", "");
                f.direction = getS(o, "direction", "up");
                f.weight    = getD(o, "weight", 0);
                out.add(f);
            }
        }
        return out;
    }

    private static String advice(InsightDTO d) {
        if (!d.atRisk) return "On track. Keep current study and attendance habits.";
        boolean attendance = d.topFactors.stream().anyMatch(f -> f.factor.equals("attendance_rate"));
        boolean failures   = d.topFactors.stream().anyMatch(f -> f.factor.equals("failure_count"));
        if (attendance && failures) return "At risk: attendance and prior failures both pull the prediction down. Prioritise attendance and remediation.";
        if (attendance) return "At risk: attendance is the dominant negative signal. Reach out about barriers to attending class.";
        if (failures)   return "At risk: prior failures weigh heavily. Recommend a study plan and tutoring.";
        return "At risk: arrange an academic advisor review.";
    }

    private static String getS(JsonObject o, String k, String def) {
        try { return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : def; }
        catch (Exception e) { return def; }
    }

    private static double getD(JsonObject o, String k, double def) {
        try { return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsDouble() : def; }
        catch (Exception e) { return def; }
    }

    /** Raised when the AI service is unreachable or refused the request. */
    public static class AiUnavailableException extends Exception {
        public AiUnavailableException(String msg) { super(msg); }
    }
}