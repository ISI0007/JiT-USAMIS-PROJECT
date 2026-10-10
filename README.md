# USAMIS — University Student Academic Management Information System

A role-based academic **Management Information System** for Jinling Institute of
Technology: student records, courses, enrollment, grades, fees, reporting, audit,
and **AI decision support**. Built as one Java web application on a single public
port; a Python AI micro-service runs on loopback and is reached only through the
Java backend.

---

## Quick start (Windows)

```
run.bat          REM start AI service (:8099) + Java app (:8080)
stop.bat         REM stop both
```

Then open **http://127.0.0.1:8080/usamis/**.

Demo accounts (course/demo only — change before any real use):

| Role | Username | Password |
|---|---|---|
| Admin | `admin001` | `admin123` |
| Registrar | `reg001` | `reg123` |
| Lecturer | `lec001` | `lec123` |
| Finance | `fin001` | `fin123` |
| Student | `stu001` | `stu123` |

---

## Architecture

- **One public port**: `8080` (Java app, context `/usamis`). All UI and API
  (including `/api/ai/*`) go through it.
- **AI micro-service**: `127.0.0.1:8099` (FastAPI, loopback-only). Not exposed.
  Uses a shared token (`X-AI-Service-Token`); **fail-closed** (503) if unset.
- **Database**: PostgreSQL 17, database `usamis`.

```
Browser ──HTTP──► :8080 Java (Servlet + embedded Tomcat)
                    ├─ AuthFilter (session + RBAC on /api/*)
                    ├─ 14 servlets / 32 URL patterns
                    ├─ DAOs → PostgreSQL (JDBC)
                    └─ AiClient ──HTTP+token──► :8099 FastAPI ──► PyTorch models
```

### Ports

| Port | Service | Exposure |
|---|---|---|
| 8080 | Java app | public (dev) |
| 8099 | AI micro-service | loopback only |
| 5432 | PostgreSQL | local |

---

## Prerequisites

- **JDK 17+** (compile & run the servlets)
- **PostgreSQL 16/17** with a `usamis` database; run
  `java/src/main/resources/schema.sql`, then `ai_migration.sql`
- **Python 3.11+** for the AI service
- The AI venv at `D:\usamis-ai\venv` (see `ai-service/README.md`)

### Configuration

Secrets/config live in **git-ignored** files. Copy the templates and edit:

```
java/src/main/resources/db.properties.example   ->  db.properties
java/src/main/resources/ai.properties.example   ->  ai.properties
ai-service/.env.example                         ->  (export vars in your shell)
```

`.gitignore` already excludes `db.properties`, `ai.properties`, and `ai-service/.env`.

---

## Modules

| # | Module | Users |
|---|---|---|
| M-01 | Authentication & Authorization | all |
| M-02 | User & Role Management | admin |
| M-03 | Student Records | admin, registrar |
| M-04 | Course Catalog | admin, registrar |
| M-05 | Enrollment | admin, registrar |
| M-06 | Grades & Transcripts | admin, lecturer |
| M-07 | Fees & Payments | admin, finance |
| M-08 | Reports & Dashboard | admin, registrar, finance |
| M-09 | System Audit Log | admin |
| M-10 | AI Decision Support | admin, registrar, lecturer, finance (student self-scoped) |

---

## Tests & verification

```
tools\smoke-test.ps1 -Base http://127.0.0.1:8080/usamis      REM 23/23
tools\verify-ai.py  --base ... --ai ... --token <token>      REM 19/19
pytest ai-service/tests                                       REM 14/14
```

Full evidence: `docs/QA_Verification_Log.md`.
Reconciled report: `docs/MIS_Report_Reconciled_v1.1.md`.
Diagrams: `docs/diagrams/` (F1–F13).

---

## Repository layout

```
java/            Java app (servlets, DAOs, models, util) + webapp/index.html (SPA)
  src/main/resources/  schema.sql, ai_migration.sql, *.properties(.example)
ai-service/      FastAPI AI micro-service + tests + train_models.py
tools/           smoke-test.ps1, verify-ai.py, diagram generators
docs/            SRS addendum, QA log, MIS report, diagrams/
_verify/         standalone Java probes (Recon/Probe/SeedScale) — not deployed
run.bat, stop.bat
```

---

## Security notes

- Passwords are stored as **BCrypt (cost 12)** hashes — never plaintext.
- Session cookie is `HttpOnly`, `SameSite=Strict`, 60-min TTL.
- `AuthFilter` enforces authentication on `/api/*`; each servlet enforces RBAC.
- Audit log is append-only; both successful and denied actions are recorded.
- The AI service is loopback-only, token-gated, and fail-closed.
- **Demo passwords are predictable** — change them before any real deployment.
