# USAMIS — Demo Script & QA Handbook

Author: Yaseen Hussain (2422486012) · Supervisor: Prof. Li Wei · Dept. Head: Prof. Zhang Minghua

---

## 1. Five-minute demo script

**Setup (before the demo):** run `run.bat`; confirm
`http://127.0.0.1:8080/usamis/` loads and `GET /api/health` returns 200.

1. **Login (M-01)** — sign in as `admin001 / admin123`. Point out: session cookie is
   HttpOnly; wrong password returns a *generic* error (no username enumeration).
2. **Dashboard (M-08)** — show live KPIs: student distribution by department, fee
   collection status (62.5%). "These are computed live from SQL, not cached."
3. **Students (M-03)** — search a partial name; show at-risk list (GPA < 2.5).
4. **Enrollment (M-05)** — attempt a **duplicate** enrollment → rejected; attempt on a
   **full** course → rejected. "Integrity is enforced below the UI."
5. **Grades (M-06)** — enter a score; show the letter + GPA auto-computed; open a
   transcript with cumulative GPA.
6. **Fees (M-07)** — record a payment; show updated balance and the defaulters list.
7. **AI Insights (M-10)** — run a risk prediction for a student; show the score, risk
   band, and forecast. Then stop the AI service and refresh → `/api/ai/*` returns 503
   while the rest of the app still works (graceful degradation).
8. **Audit (M-09)** — show the append-only log, including the ACCESS_DENIED from the
   RBAC probes.
9. **RBAC (M-01/05)** — log in as `stu001`; show the student sees only their own data,
   and `/audit` and `/ai/status` are 403.

**Closing line:** "56 automated checks pass; RBAC and anti-IDOR were verified per role."

---

## 2. Fifteen likely examiner questions & answers

1. **Is this an MIS or just a database app?** It transforms data into role-filtered
   information and supports decisions (at-risk, fee collection, capacity, audit).
2. **Why one port?** Single user-facing surface; the AI service is internal (loopback),
   reached only through the Java backend.
3. **How are passwords stored?** BCrypt hashes, cost 12 — never plaintext.
4. **Can an admin read a user's password?** No. Only a reset is possible.
5. **How do you prevent duplicate enrollments?** UNIQUE(student, course, semester) +
   application check.
6. **How is GPA computed?** Live via `v_student_gpa` (SQL AVG), never stored redundantly.
7. **What happens if the AI service is down?** `/api/ai/*` returns 503; the rest of the
   app is unaffected.
8. **How do you stop SQL injection?** PreparedStatement (parameterized) everywhere.
9. **How do you stop a student reading another student's data (IDOR)?** Every endpoint
   self-scopes by session identity; verified with probes.
10. **Is the audit log tamper-proof?** It is append-only at the application layer; no
    update/delete routes exist.
11. **How many permissions/roles?** 13 permissions across 5 roles, checked per request.
12. **Are the AI metrics real?** Yes — read from the deployed model metadata. The models
    are trained on a **synthetic** generator; this is disclosed.
13. **Why no ROC curve?** Training did not persist ROC/confusion artifacts, so none are
    reported (we do not invent them).
14. **Does it scale?** Indexed access on all FKs and hot columns; targets are specified
    but not benchmarked in this report.
15. **What would you do next (v2.0)?** Payment gateway, notifications, mobile client,
    real-data AI retraining, PDF export.

---

## 3. Glossary

| Term | Meaning |
|---|---|
| MIS | Management Information System |
| RBAC | Role-Based Access Control |
| 3NF | Third Normal Form (normalisation) |
| FK | Foreign Key |
| BCrypt | Adaptive password-hashing function |
| SPAs | Single-Page Application |
| MLP | Multilayer Perceptron (neural network) |
| LSTM | Long Short-Term Memory (sequence network) |
| KPI | Key Performance Indicator |
| IDOR | Insecure Direct Object Reference |
| ILIKE | PostgreSQL case-insensitive pattern match |
| Fail-closed | Deny by default when configuration is missing |

---

## 4. Verification commands (reproduce the evidence)

```
tools\smoke-test.ps1 -Base http://127.0.0.1:8080/usamis
& "D:\usamis-ai\venv\Scripts\python.exe" tools\verify-ai.py --base http://127.0.0.1:8080/usamis --ai http://127.0.0.1:8099 --token usamis-ai-dev-token-2026
& "D:\usamis-ai\venv\Scripts\python.exe" -m pytest ai-service\tests
```

Expected: 23/23, 19/19, 14/14.
