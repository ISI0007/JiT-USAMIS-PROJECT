-- ═══════════════════════════════════════════════
--  USAMIS — AI Module Migration
--  Run AFTER schema.sql (and seed.sql for demo data).
--  Additive only: does not touch existing tables or rows.
-- ═══════════════════════════════════════════════

-- ─── AI PREDICTION HISTORY ───────────────────
-- Stores every prediction the Java backend requests from the AI service so the
-- UI can show history and so model versions can be audited. References the
-- EXISTING students table (students.id, SERIAL) — no student data is duplicated.
CREATE TABLE IF NOT EXISTS ai_prediction (
    prediction_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id        BIGINT      NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    model_name        VARCHAR(100) NOT NULL,      -- e.g. mlp_performance
    model_version     VARCHAR(50)  NOT NULL,      -- e.g. 1.0.0-synth
    pred_type         VARCHAR(30)  NOT NULL DEFAULT 'PERFORMANCE'
                      CHECK (pred_type IN ('PERFORMANCE','FORECAST','RECOMMENDATION')),
    predicted_score   NUMERIC(5,2),               -- 0..100 for PERFORMANCE
    risk_probability  NUMERIC(6,5),               -- 0..1
    at_risk           BOOLEAN NOT NULL DEFAULT FALSE,
    payload           JSONB,                      -- full response (factors, forecast...)
    requested_by      INT REFERENCES users(id),   -- which user triggered the call
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_pred_student ON ai_prediction(student_id);
CREATE INDEX IF NOT EXISTS idx_ai_pred_created ON ai_prediction(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_pred_risk    ON ai_prediction(at_risk);

-- ─── AI PERMISSION ───────────────────────────
-- VIEW_AI      — can read predictions / insights
-- MANAGE_AI    — can trigger training & see all predictions
INSERT INTO permissions (name, module) VALUES
  ('VIEW_AI',   'ai'),
  ('MANAGE_AI', 'ai')
ON CONFLICT (name) DO NOTHING;

-- Admin gets both
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.name = 'admin' AND p.name IN ('VIEW_AI','MANAGE_AI')
ON CONFLICT DO NOTHING;

-- Registrar + lecturer + finance can view insight; students see their own only.
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.name IN ('registrar','lecturer','finance') AND p.name = 'VIEW_AI'
ON CONFLICT DO NOTHING;

-- Students may view their own AI insight (self-scoped in the servlet).
INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id FROM roles r, permissions p
  WHERE r.name = 'student' AND p.name = 'VIEW_AI'
ON CONFLICT DO NOTHING;
