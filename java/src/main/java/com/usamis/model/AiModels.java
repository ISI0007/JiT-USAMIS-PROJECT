package com.usamis.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Models for the AI module. Kept separate from Models.java so the AI addition
 * is self-contained and the existing domain models are untouched.
 */
public final class AiModels {
    private AiModels() {}

    /** A persisted prediction row (ai_prediction table). */
    public static class Prediction {
        public long   predictionId;
        public int    studentId;
        public String studentNo;      // joined for display
        public String studentName;    // joined for display
        public String modelName;
        public String modelVersion;
        public String predType;
        public Double predictedScore;
        public Double riskProbability;
        public boolean atRisk;
        public String payload;        // raw JSON from the AI service
        public Integer requestedBy;
        public LocalDateTime createdAt;
    }

    /** Response DTO returned to the SPA for a performance prediction. */
    public static class InsightDTO {
        public int    studentId;
        public String studentNo;
        public String studentName;
        public double predictedScore;
        public double riskProbability;
        public boolean atRisk;
        public String modelName;
        public String modelVersion;
        public String trainedOn;
        public List<Factor> topFactors;
        public String advice;
    }

    /** One directional explanation factor. */
    public static class Factor {
        public String factor;
        public String direction;  // "up" | "down"
        public double weight;
    }

    /** AI service health snapshot for the admin panel. */
    public static class ServiceStatus {
        public boolean reachable;
        public String  status;
        public String  version;
        public boolean tokenConfigured;
        public java.util.Map<String, Boolean> models;
        public String  message;
    }
}