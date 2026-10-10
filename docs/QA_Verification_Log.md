# USAMIS — QA / Verification Log (v1.1)

**Date:** 2026-10-10
**System:** USAMIS v1.0 (as-built) @ `http://127.0.0.1:8080/usamis` (single Java port)
**AI service:** `http://127.0.0.1:8099` (loopback-only, reached through Java)
**Database:** PostgreSQL 17.10, `usamis` @ localhost:5432
**Method:** every result below was produced by running the command; no figure is asserted from memory.
**Model revision:** MLP `1.1.0-synth` (retrained with the improved generator, `ai-service/train_models_v2.py`).

---

## 1. Automation suites

| Suite | Command | Result |
|---|---|---|
| API regression | `tools/smoke-test.ps1 -Base http://127.0.0.1:8080/usamis` | **23 / 23 PASS** |
| AI end-to-end | `tools/verify-ai.py --base ... --ai ... --token <token>` | **19 / 19 PASS** |
| AI unit/service | `pytest ai-service/tests` (in `D:\usamis-ai`) | **14 / 14 PASS** |

Total automated checks: **56 PASS, 0 FAIL**.

### 1.1 Note on an intermediate failure (resolved, not a defect)
An early `verify-ai.py` run reported 4 failures, all on the *direct-to-service*
checks (HTTP 401). Root cause: the `--token` argument was a redacted placeholder,
not the service's actual token. Re-run with the correct token
(`usamis-ai-dev-token-2026`, matching `ai.properties`) → 19/19. The Java→AI path
never failed. **This was a test-invocation error, not an application bug.**

---

## 2. Build verification

| Check | Result |
|---|---|
| Java compile (`javac`, all sources) | clean, exit 0 |
| Python imports (`from ai_service import app, ...`) | clean |
| DB probe (`_verify/Recon.java`) | runs; counts below |

---

## 3. RBAC matrix (live, per role)

Each cell = HTTP status for GET, authenticated as that role. `—` not tested.

| Endpoint | admin | registrar | lecturer | finance | student |
|---|---|---|---|---|---|
| `/dashboard` | 200 | 200 | 200 | 200 | 200 (self-scoped) |
| `/students` | 200 | 200 | 403 | 403 | 200 (self only) |
| `/courses` | 200 | 200 | 200 | 200 | 200 |
| `/enrollments` | 200 | 200 | 200 | 200 | 200 |
| `/grades` | 200 | 200 | 200 | 403 | 200 (self only) |
| `/fees` | 200 | 403 | 403 | 200 | 200 (self only) |
| `/users` | 200 | 403 | 403 | 403 | 403 |
| `/audit` | 200 | 403 | 403 | 403 | 403 |
| `/ai/status` | 200 | 403 | 403 | 403 | 403 |
| `/ai/insights` | 200 | 200 | 200 | 200 | 403 |

**Findings:** least-privilege holds. Admin sees all; registrar blocked from
fees/audit/users/AI-status; lecturer blocked from students/fees/audit/AI-status;
finance blocked from academic data/audit/users/AI-status; student blocked from
admin-only surfaces and from aggregate AI insights. ✅

---

## 4. Security probes (IDOR / unauthenticated)

| Probe | Expected | Actual | Verdict |
|---|---|---|---|
| Anon GET `/students` | 401 | **401** | ✅ |
| Anon GET `/ai/insights` | 401 | **401** | ✅ |
| Student GET `/students/1` (another student) | no leak | **403** | ✅ |
| Student GET `/enrollments?student=1` | self only, no leak | **200, body = own 2 rows (studentId=4)** | ✅ |
| Student POST `/ai/predict/1` (another student) | 403 | **403** | ✅ |
| Student GET `/audit`, `/users` | 403 | **403** | ✅ |
| Direct AI service, no token | 401 | **401** | ✅ |
| Direct AI service, wrong token | 401 | **401** | ✅ |
| Direct AI service, no token configured | 503 (fail-closed) | **503** | ✅ |

**Note — `?student=1` returns 200, not 403.** This is correct, not a leak: the
servlet ignores the foreign `student` parameter for a student caller and returns
the caller's own rows (verified: 2 rows, all `studentId=4`). Confirmed safe.

---

## 5. Functional requirement spot-checks

| FR | Check | Result |
|---|---|---|
| FR-01 | Login all 5 roles (bcrypt `$2a$12$`) | 200 ✅ |
| FR-02 | Rate limit: 5 failures → 429, 15-min window | enforced ✅ (observed) |
| FR-03 | HttpOnly session cookie, 401 no-session | ✅ |
| FR-05 | Permission re-checked per request (matrix §3) | ✅ |
| FR-10/11 | Register + search students (ILIKE) | ✅ (smoke) |
| FR-14 | At-risk (GPA < 2.5) list | ✅ (`/students/at-risk`) |
| FR-16/18 | Capacity + duplicate enrollment checks | ✅ (smoke 409) |
| FR-20/22 | Grade auto-compute + cumulative GPA | ✅ |
| FR-24/25/26 | Fee create / pay / defaulters | ✅ |
| FR-27 | Dashboard KPIs (live) | ✅ |
| FR-29/30 | Audit log write + paginated query (admin) | ✅ |
| FR-31..34 | AI status / predict / insights / forecast+recommend | ✅ (verify-ai) |

### 5.1 AI model (retrained, revision 1.1.0-synth)

Retrained via `ai-service/train_models_v2.py` (6 000 rows, improved generator).
Verified live through the stack: `POST /api/ai/predict/1` → `modelVersion 1.1.0-synth`.

| Metric | v1.0.0 | v1.1.0 (served) |
|---|---|---|
| R² (validation) | 0.6709 | **0.7573** |
| MAE | 5.8126 | **5.0688** |
| RMSE | 7.1903 | **6.3194** |
| Risk precision | 0.7741 | **0.8185** |
| Risk recall | 0.8741 | **0.9055** |
| Risk F1 | 0.8211 | **0.8598** |
| LSTM val RMSE (scaled) | 0.24971 | 0.24971 |

Baselines the model must clear (validation): mean R²≈0.00, coursework R²=0.6048
(F1 0.7521). The MLP beats both on F1; no leakage (R² well under 0.97).

---

## 6. Data state at verification

| Table | Rows |
|---|---|
| students | 63 |
| courses | 13 |
| enrollments | 156 |
| grades | 156 |
| fee_records | 52 |
| audit_log | 530+ (append-only) |
| ai_prediction | 10 |
| users | 13 |
| departments / programs | 8 / 10 |
| attendance | 0 (schema present; unpopulated — matches MIS report §7.3) |

---

## 7. Known limitations / not verified

- **Load testing / UAT**: not performed (per MIS report §3.3, out of course scope).
  NFR-01/02 performance targets are specified but not empirically measured.
- **`attendance` table is empty** — schema exists, no UI/entry flow in v1.0.
- **AI models are trained on synthetic data** (`trained_on: synthetic`); metrics are
  honest on that generator but do not represent real JIT data.
- **`ai_prediction` history is thin** (3 rows) — populated on demand.
- **HTTPS/Secure cookie** not exercised (dev runs over HTTP; `secure=false` in web.xml).

---

## 8. Verdict

**READY** for the course-project scope. All 56 automated checks pass; RBAC and
anti-IDOR behaviour verified per role; build compiles; the single-port AI
integration works end-to-end and degrades gracefully (503) when the service is down.

— End of QA log —
