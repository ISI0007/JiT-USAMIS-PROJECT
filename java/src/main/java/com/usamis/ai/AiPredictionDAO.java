package com.usamis.ai;

import com.usamis.model.AiModels.*;
import com.usamis.model.Models.*;
import com.usamis.util.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for AI predictions (ai_prediction table).
 *
 * WHY a dedicated DAO: the AI table is written by a different subsystem than the
 * core academic data; keeping it separate means the existing DAOs (and their tests)
 * stay untouched.
 */
public class AiPredictionDAO {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionDAO.class);

    /** Insert a prediction row; returns the generated id (or -1 on failure). */
    public long save(int studentId, String modelName, String modelVersion, String predType,
                     Double predictedScore, Double riskProbability, boolean atRisk,
                     String payloadJson, Integer requestedBy) {
        String sql = "INSERT INTO ai_prediction " +
            "(student_id, model_name, model_version, pred_type, predicted_score, " +
            " risk_probability, at_risk, payload, requested_by) " +
            "VALUES (?,?,?,?,?,?,?,?::jsonb,?) RETURNING prediction_id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setString(2, modelName);
            ps.setString(3, modelVersion);
            ps.setString(4, predType == null ? "PERFORMANCE" : predType);
            if (predictedScore != null) ps.setDouble(5, predictedScore); else ps.setNull(5, Types.NUMERIC);
            if (riskProbability != null) ps.setDouble(6, riskProbability); else ps.setNull(6, Types.NUMERIC);
            ps.setBoolean(7, atRisk);
            ps.setString(8, payloadJson);
            if (requestedBy != null) ps.setInt(9, requestedBy); else ps.setNull(9, Types.INTEGER);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("save prediction for student {}", studentId, e);
            return -1L;
        }
    }

    /** Recent predictions for one student (self-scoped insight history). */
    public List<Prediction> findByStudent(int studentId, int limit) {
        String sql = "SELECT p.*, s.student_id AS st_no, s.first_name || ' ' || s.last_name AS st_name " +
            "FROM ai_prediction p JOIN students s ON p.student_id = s.id " +
            "WHERE p.student_id = ? ORDER BY p.created_at DESC LIMIT ?";
        return query(sql, studentId, limit);
    }

    /** Recent predictions across all students (admin / staff view). */
    public List<Prediction> findRecent(int limit) {
        String sql = "SELECT p.*, s.student_id AS st_no, s.first_name || ' ' || s.last_name AS st_name " +
            "FROM ai_prediction p JOIN students s ON p.student_id = s.id " +
            "ORDER BY p.created_at DESC LIMIT ?";
        List<Prediction> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("findRecent predictions", e);
        }
        return list;
    }

    /** Students flagged at-risk in their most recent prediction — the advisor queue. */
    public List<Prediction> findAtRisk(int limit) {
        String sql = "SELECT DISTINCT ON (p.student_id) p.*, " +
            "  s.student_id AS st_no, s.first_name || ' ' || s.last_name AS st_name " +
            "FROM ai_prediction p JOIN students s ON p.student_id = s.id " +
            "WHERE p.pred_type = 'PERFORMANCE' " +
            "ORDER BY p.student_id, p.created_at DESC";
        List<Prediction> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Prediction p = map(rs);
                if (p.atRisk) list.add(p);
            }
        } catch (SQLException e) {
            log.error("findAtRisk predictions", e);
        }
        // Newest first, cap to limit
        list.sort((a, b) -> {
            if (a.createdAt == null || b.createdAt == null) return 0;
            return b.createdAt.compareTo(a.createdAt);
        });
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    public Optional<Prediction> findLatestForStudent(int studentId) {
        List<Prediction> list = findByStudent(studentId, 1);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    private List<Prediction> query(String sql, int studentId, int limit) {
        List<Prediction> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            log.error("query predictions for student {}", studentId, e);
        }
        return list;
    }

    private Prediction map(ResultSet rs) throws SQLException {
        Prediction p = new Prediction();
        p.predictionId = rs.getLong("prediction_id");
        p.studentId    = rs.getInt("student_id");
        p.studentNo    = rs.getString("st_no");
        p.studentName  = rs.getString("st_name");
        p.modelName    = rs.getString("model_name");
        p.modelVersion = rs.getString("model_version");
        p.predType     = rs.getString("pred_type");
        double sc = rs.getDouble("predicted_score"); p.predictedScore = rs.wasNull() ? null : sc;
        double rp = rs.getDouble("risk_probability"); p.riskProbability = rs.wasNull() ? null : rp;
        p.atRisk       = rs.getBoolean("at_risk");
        p.payload      = rs.getString("payload");
        int rb = rs.getInt("requested_by"); p.requestedBy = rs.wasNull() ? null : rb;
        Timestamp ca = rs.getTimestamp("created_at");
        p.createdAt = ca != null ? ca.toLocalDateTime() : null;
        return p;
    }
}