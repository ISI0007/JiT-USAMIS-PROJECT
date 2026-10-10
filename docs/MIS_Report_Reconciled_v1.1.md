# USAMIS — MIS Investigation Report (Reconciled, v1.1)

**System:** University Student Academic Management Information System (USAMIS)
**Institution:** Jinling Institute of Technology (金陵科技学院), Nanjing, China
**Course:** Management Information Systems / Database Systems
**Prepared by:** Yaseen Hussain · Student ID: 2422486012
**Supervisor:** Prof. Li Wei
**Department Head:** Prof. Zhang Minghua
**Version:** 1.1 (Reconciled with as-built system)
**Date:** October 2026

> This v1.1 reconciles the original v1.0 MIS Investigation Report and the SRS with
> the **actual deployed system**. Every figure here was produced by running the
> system (see `docs/QA_Verification_Log.md`). Where the original documents and the
> code diverged, the code is authoritative and the divergence is disclosed.

---

## 1. Executive Summary

USAMIS is a web-based, role-based academic Management Information System. It is a
genuine MIS — not a database front-end — because it **transforms raw academic data
into role-filtered management information** and supports real decisions: early
at-risk intervention, fee-collection monitoring, course-capacity planning, and
security auditing.

The system runs as **one Java application on a single port (8080)**. A separate
Python AI micro-service runs on loopback (8099) and is reached *only* through the
Java backend; it is not a second user-facing port. This was verified live.

**Status:** READY for course scope. 56 automated checks pass (smoke 23/23,
AI end-to-end 19/19, AI unit 14/14); per-role RBAC and anti-IDOR behaviour verified.

---

## 2. What the System Does (modules)

| # | Module | Key functions | Primary users |
|---|---|---|---|
| M-01 | Authentication & Authorization | Login, logout, session, RBAC per request | All |
| M-02 | User & Role Management | Add/edit/deactivate users, RBAC matrix view | Admin |
| M-03 | Student Records | Register, search, update, soft-deactivate, at-risk | Admin, Registrar |
| M-04 | Course Catalog | Create/edit/cancel; capacity tracking | Admin, Registrar |
| M-05 | Enrollment | Enroll/drop; duplicate + capacity checks | Admin, Registrar |
| M-06 | Grades & Transcripts | Enter/update scores; auto letter+GPA; transcript | Admin, Lecturer |
| M-07 | Fees & Payments | Fee records, payments, defaulters | Admin, Finance |
| M-08 | Reports & Dashboard | Role-aware KPIs, 6 management reports | Admin, Registrar, Finance |
| M-09 | System Audit Log | Immutable write/login/denial logging; paginated query | Admin |
| **M-10** | **AI Decision Support** | Performance risk prediction, enrollment forecast, course recommendation | Admin, Registrar, Lecturer, Finance (self-scoped for students) |

M-10 is new relative to the original SRS. It corresponds to the "AI-Powered Student
Performance Prediction" recommendation in the original MIS report §7.3, which the
project has since implemented. It therefore appears here as a formal module rather
than a future item. See `docs/SRS_Reconciliation_Addendum.md` for its full
requirements (FR-31…FR-34) and RBAC.

---

## 3. Reconciliation with the original documents

The original SRS/MIS figures understated the built system. Verified deltas:

| Metric | Original doc | As-built (verified) |
|---|---|---|
| Tables | 12 | **15** |
| Computed views | — | **3** |
| Permissions | 11 | **13** |
| Roles | 5 | **5** |
| FK constraints | 14 | **21** |
| Servlets / URL patterns | 21 endpoints | **14 servlets / 32 patterns** |
| Functional requirements | FR-01…FR-30 | FR-01…**FR-34** (adds M-10) |
| Modules | M-01…M-09 | M-01…**M-10** |

The pre-existing drift (`instructors`, `attendance` tables; `/auth/me`,
`/auth/register`, `/lookups/*`, `/permissions*` routes) predates M-10 — the original
counts were already conservative.

---

## 4. Information Flow (verified)

```
RAW DATA → [People + Process + Technology] → INFORMATION → DECISION
score=97 → computeGrade() → 'A+', 4.0 GPA   → student on track
fees: Σtotal, Σpaid       → 'Collection rate: 62.5%' → chase defaulters
```

The same underlying data yields different information per role:

| Role | Sees | Decides |
|---|---|---|
| Admin | all KPIs, at-risk count, audit feed | intervention, security review |
| Registrar | roster + GPA, capacity, pass rates | scheduling, advising |
| Lecturer | own-course rosters, score distribution | re-assessment, support |
| Finance | collection rate, outstanding, defaulters | payment follow-up |
| Student | own GPA, enrollments, fees | next-semester planning |

---

## 5. Decision Support (verified outputs)

| Decision | Role | Source (live SQL) |
|---|---|---|
| Who needs intervention? | Registrar/Admin | students with GPA < 2.5 |
| Is fee collection on track? | Finance/Admin | SUM(total), SUM(paid) |
| Which courses underperform? | Registrar/Admin | pass rate per course |
| Is a course full? | Registrar | enrolled vs max_enrollment |
| What did user X do? | Admin | audit_log filtered |
| What is my GPA? | Student | AVG(gpa_points) self-scoped |

---

## 6. Data Architecture

- **Normalization:** 3NF. Departments/programs/instructors stored once and referenced
  by FK; no transitive duplication.
- **Integrity:** 21 FK constraints (verified). Composite `UNIQUE(student_id,
  course_id, semester)` on enrollments prevents duplicates even under concurrency.
- **Views:** `v_student_gpa`, `v_fee_summary`, `v_course_enrollment`.
- **GPA is computed live** (SQL AVG), never stored as a redundant column.

---

## 7. Security Architecture (defense in depth)

| Layer | Mechanism | Verified |
|---|---|---|
| Input | server-side validation; parameterized SQL | ✅ |
| Application | `AuthFilter` on `/api/*`; per-servlet RBAC | ✅ matrix §QA-3 |
| Database | UNIQUE / CHECK / FK constraints | ✅ 21 FKs |
| Passwords | BCrypt cost 12 (`$2a$12$`) | ✅ |
| Session | HttpOnly, SameSite=Strict, 60-min TTL, 401 without session | ✅ |
| Audit | immutable append-only log; ACCESS_DENIED recorded | ✅ |
| Anti-IDOR | students self-scoped on every endpoint | ✅ QA-4 |

---

## 8. AI Decision Support (M-10) — because the original report flagged it

The original investigation listed AI prediction as a v2.0 recommendation. It is now
implemented and verified:

- **Isolation:** FastAPI service on loopback, token-gated (`X-AI-Service-Token`),
  fail-closed (503 if unconfigured). Never exposed on the app port.
- **Single port:** all AI access is via `/api/ai/*` on 8080. Verified.
- **Models:** `mlp_performance` (score + risk, threshold 60; validation R² **0.7573**, RMSE **6.3194**, risk precision **0.8185** / recall **0.9055** / F1 **0.8598** on synthetic validation), `lstm_enrollment` (forecast, val RMSE 0.2497 scaled), `graph_course_recommender`.
- **Graceful degradation:** service down → `/api/ai/*` 503; rest of app unaffected.
- **Honesty:** models are `trained_on: synthetic`; forecast returns 422 (not a
  fabricated number) below 4 semesters of history.

---

## 9. Findings

1. **Fragmentation → single schema.** One PostgreSQL schema across all ten modules
   eliminates the multi-Excel fragmentation the original report diagnosed.
2. **RBAC is the baseline, not a luxury.** 5 roles, 13 permissions, enforced per
   request; verified per-role.
3. **Real-time dashboard is the MIS differentiator.** Live SQL KPIs, never cached.
4. **Audit trail closes the accountability gap.** Append-only, immutable at app level.
5. **The investigation's own AI recommendation is now delivered** (M-10), one module
   beyond the original SRS scope.

---

## 10. Conclusions

USAMIS satisfies the MIS definition: it **collects, processes, stores, transforms,
distributes, and protects** academic information, and it supports decisions at every
role level. It is architecturally aligned with the benchmarked real-world systems
(CUHK CUSIS, ITM MIS, Jin 2015) while remaining appropriately scoped. The reconciled
figures in this v1.1 are traceable to live verification (see QA log).

---

## 11. Recommendations for v2.0 (unchanged + AI follow-on)

| Enhancement | Priority | Note |
|---|---|---|
| Online payment gateway | HIGH | complete the fee loop |
| Email/SMS notifications | HIGH | reminders, grade release |
| Mobile app | HIGH | responsive now; native later |
| QR attendance | MEDIUM | `attendance` table already present, empty |
| **Real-data AI training** | MEDIUM | replace synthetic with real JIT history |
| PDF export (transcripts/reports) | MEDIUM | official documents |
| Parent portal | LOW | view-only |
| Cloud deployment | LOW | availability target |

---

## 12. Verification summary

| Suite | Result |
|---|---|
| smoke-test.ps1 | 23 / 23 |
| verify-ai.py | 19 / 19 |
| pytest | 14 / 14 |
| RBAC matrix | correct per role |
| IDOR probes | no leaks |
| **Total** | **56 / 56 automated + manual matrix** |

Full detail: `docs/QA_Verification_Log.md`.

---

*Note: byline fields marked `[...]` are placeholders pending the author's details,
as requested. All technical figures are verified against the running system.*

— End of Report —
