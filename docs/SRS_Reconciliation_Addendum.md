# USAMIS — SRS Reconciliation Addendum (v1.1)

**Purpose.** Reconcile the original SRS (v1.0, IEEE 830-1998) and the MIS Investigation
Report with the *as-built* system, so every figure in the submitted documents is
traceable to the running code and database. Prepared after a read-only
reconnaissance of the deployed system on 2026-10-10.

This addendum does **not** rewrite the SRS. It records the deltas and adds the one
module that was built but not yet specified: **M-10 (AI Decision Support)**.

---

## 1. Count reconciliation

The original SRS §6.4 says "12 tables", §1.3/§8.2 implies "11 permissions", the endpoint
summary (§6.2) lists "21 endpoints", and the MIS report §6.3.2 says "14 FOREIGN KEY
constraints". The as-built system differs. Verified counts below come from
`pg_tables`, `pg_views`, `information_schema.table_constraints`, and the servlet
`@WebServlet` annotations.

| Metric | Original doc | As-built (verified) | Notes |
|---|---|---|---|
| Database tables | 12 | **15** | +`instructors`, +`attendance`, +`ai_prediction` |
| Computed views | (implied) | **3** | `v_student_gpa`, `v_fee_summary`, `v_course_enrollment` |
| Permissions | 11 | **13** | +`VIEW_AI`, +`MANAGE_AI` |
| Roles | 5 | **5** | ✅ match |
| FK constraints | 14 | **21** | counted across all public tables |
| Servlets / URL patterns | 21 endpoints | **14 servlets / 32 patterns** | wildcard patterns cover `/{id}` sub-routes |
| Audit-log rows | — | 530 (at recon) | immutable, append-only |

The two tables and several route patterns the original documents omit
(`instructors`, `attendance`; `/api/auth/me`, `/api/auth/register`,
`/api/lookups/*`, `/api/permissions*`) predate the AI work — the original counts
were already conservative.

---

## 2. Module M-10 — AI Decision Support (new, formalised)

The MIS Investigation Report §7.3 lists "AI-Powered Student Performance Prediction"
as a **Version 2.0 recommendation**. The as-built system already implements it, so
it is promoted here to a first-class module, consistent with the SRS's own
"future enhancement" framing.

### 2.1 Design

- **Service:** a separate Python FastAPI micro-service (`ai-service/`), reachable only
  on loopback (`127.0.0.1:8099`), token-gated (`X-AI-Service-Token`), fail-closed.
- **Integration:** the Java backend calls the service over HTTP and exposes everything
  through the **single application port (8080)** under `/api/ai/*`. No second
  user-facing port. If the service is unreachable, `/api/ai/*` returns HTTP 503 —
  the rest of the application is unaffected (graceful degradation, NFR-11).
- **Models:**
  - `mlp_performance` — MLP regressor predicting a 0–100 performance score and a
    derived risk probability (threshold 60). On synthetic validation: R² 0.671,
    MAE 5.81, risk recall 0.874.
  - `lstm_enrollment` — LSTM for enrollment forecasting (needs ≥4 semesters history).
  - `graph_course_recommender` — prerequisite-aware course recommender.

### 2.2 Functional requirements

**FR-31 — AI Service Status (Admin).** GET `/api/ai/status` — reachability, token
config, loaded models. Admin only.

**FR-32 — Performance Prediction.** POST `/api/ai/predict/{studentId}` — build the
feature vector from existing tables (students, enrollments, grades, attendance),
call the model, persist to `ai_prediction`, return predicted score, risk
probability, at-risk flag, and top contributing factors. Staff may predict any
student; a student may predict only their own record.

**FR-33 — Insights Retrieval.** GET `/api/ai/insights`, `/api/ai/insights/at-risk`,
`/api/ai/me`, `/api/ai/student/{id}` — recent predictions, advisor at-risk queue,
and per-student history. Staff see aggregates; students are self-scoped.

**FR-34 — Forecast & Recommendations.** GET `/api/ai/forecast/{courseId|all}` and
`/api/ai/recommend/{studentId}` — enrollment forecasting (HTTP 422 below 4 semesters
of history — honest, not fabricated) and prerequisite-aware course recommendations.

### 2.3 RBAC for M-10

| Role | VIEW_AI | MANAGE_AI |
|---|---|---|
| admin | ✅ | ✅ |
| registrar | ✅ | — |
| lecturer | ✅ | — |
| finance | ✅ | — |
| student | ✅ (self-scoped only) | — |

### 2.4 Persistence

New table `ai_prediction` (15th table): prediction history with model name/version,
predicted score, risk probability, at-risk flag, JSONB payload, and the requesting
user. FK to `students(id)` and `users(id)`. Additive only — no change to existing tables.

---

## 3. Updated traceability

| Area | Count |
|---|---|
| Functional requirements | FR-01…FR-30 (original) + **FR-31…FR-34 (M-10)** = 34 |
| Modules | M-01…M-09 (original) + **M-10 (AI)** = 10 |
| Non-functional requirements | 17 (unchanged) |
| Roles | 5 |
| Permissions | 13 |
| Tables | 15 |
| Views | 3 |

---

## 4. Verification statement

Every count in this addendum was produced by running queries against the live
database and reading the servlet annotations — not asserted from memory. The
procedures are reproducible:

- Tables/views/permissions/roles/FKs: `_verify/Recon.java` (JDBC probe).
- Endpoint patterns: grep of `@WebServlet` annotations across `com/usamis/servlet`.
- AI end-to-end: `tools/verify-ai.py` (19 checks), `tools/smoke-test.ps1` (23 checks),
  `ai-service/tests` (14 pytest checks).

See the accompanying QA/verification log for pass/fail results.

— End of Addendum —
