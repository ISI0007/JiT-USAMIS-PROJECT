"""
Build the USAMIS professional engineering report (DOCX) from VERIFIED data only.
Every number here is traceable: schema.sql, servlet annotations, live QA runs,
and D:\\usamis-ai\\models\\*.json.
Author: Yaseen Hussain (2422486012)  |  Supervisor: Prof. Li Wei  |  Dept Head: Prof. Zhang Minghua
"""
import os, json
from docx import Document
from docx.shared import Pt, Inches, RGBColor, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

ROOT = r"C:\Users\Admin\.openclaw\workspace\JiT-USAMIS-PROJECT"
DOCS = os.path.join(ROOT, "docs")
DIAG = os.path.join(DOCS, "diagrams")
OUT  = os.path.join(DOCS, "USAMIS_Engineering_Report.docx")

NAVY = RGBColor(0x12, 0x35, 0x5B)
GREY = RGBColor(0x55, 0x55, 0x55)

# --- load real model metrics ---
def metrics():
    p = r"D:\usamis-ai\models\mlp_performance.json"
    with open(p, encoding="utf-8") as f:
        mlp = json.load(f)
    p2 = r"D:\usamis-ai\models\lstm_enrollment.json"
    with open(p2, encoding="utf-8") as f:
        lstm = json.load(f)
    return mlp, lstm
MLP, LSTM = metrics()

doc = Document()

# base style
st = doc.styles["Normal"]
st.font.name = "Calibri"; st.font.size = Pt(11)
st.element.rPr.rFonts.set(qn("w:eastAsia"), "Calibri")

def H(text, level=1):
    h = doc.add_heading(text, level=level)
    for r in h.runs:
        r.font.color.rgb = NAVY
    return h

def P(text, bold=False, italic=False, size=11, align=None, color=None):
    p = doc.add_paragraph()
    if align: p.alignment = align
    r = p.add_run(text); r.bold = bold; r.italic = italic; r.font.size = Pt(size)
    if color: r.font.color.rgb = color
    return p

def bullet(text):
    return doc.add_paragraph(text, style="List Bullet")

def table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Light Grid Accent 1"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hc = t.rows[0].cells
    for i, h in enumerate(headers):
        hc[i].text = ""
        run = hc[i].paragraphs[0].add_run(h); run.bold = True; run.font.size = Pt(10)
    for row in rows:
        cells = t.add_row().cells
        for i, v in enumerate(row):
            cells[i].text = ""
            run = cells[i].paragraphs[0].add_run(str(v)); run.font.size = Pt(10)
    return t

def figure(path, caption, width=6.2):
    if os.path.exists(path):
        doc.add_picture(path, width=Inches(width))
        doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
        cap = doc.add_paragraph()
        cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = cap.add_run(caption); r.italic = True; r.font.size = Pt(9); r.font.color.rgb = GREY
    else:
        P("[missing figure: %s]" % os.path.basename(path), italic=True, color=GREY)

# ============================================================ TITLE PAGE
tp = doc.add_paragraph(); tp.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = tp.add_run("JINLING INSTITUTE OF TECHNOLOGY"); r.bold = True; r.font.size = Pt(16); r.font.color.rgb = NAVY
sp = doc.add_paragraph(); sp.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = sp.add_run("School of Computer Engineering / Software Engineering"); r.font.size = Pt(11); r.font.color.rgb = GREY
doc.add_paragraph()
t = doc.add_paragraph(); t.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = t.add_run("University Student Academic Management\nInformation System (USAMIS)"); r.bold = True; r.font.size = Pt(24); r.font.color.rgb = NAVY
s = doc.add_paragraph(); s.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = s.add_run("Engineering Design & Implementation Report"); r.font.size = Pt(14); r.italic = True
doc.add_paragraph(); doc.add_paragraph()
info = doc.add_table(rows=6, cols=2); info.alignment = WD_TABLE_ALIGNMENT.CENTER
info.style = "Table Grid"
rows = [
 ("Prepared by", "Yaseen Hussain"),
 ("Student ID", "2422486012"),
 ("Supervisor", "Prof. Li Wei"),
 ("Department Head", "Prof. Zhang Minghua"),
 ("Document version", "1.1 (reconciled with as-built system)"),
 ("Date", "October 2026"),
]
for i,(k,v) in enumerate(rows):
    info.rows[i].cells[0].text = k
    info.rows[i].cells[1].text = v
    info.rows[i].cells[0].paragraphs[0].runs[0].bold = True
doc.add_page_break()

# ============================================================ 1. INTRO
H("1. Introduction", 1)
P("This report documents the design, implementation and verification of the University "
  "Student Academic Management Information System (USAMIS). It is written from the "
  "engineering perspective: the requirements were supplied by a requirements engineer "
  "(the Software Requirements Specification, SRS), and this document describes how the "
  "system was designed and built to satisfy them, and the evidence that it does.")
P("USAMIS is a genuine Management Information System rather than a database front-end: "
  "it collects, processes, stores, transforms and distributes academic data, and it "
  "supports management decisions such as early at-risk intervention, fee-collection "
  "monitoring, and course-capacity planning. Section 12 gives the verified evidence.")

H("1.1 Scope", 2)
bullet("In scope: student records, course catalogue, enrollment, grades/transcripts, "
       "fees/payments, reporting & dashboards, system audit, and AI decision support.")
bullet("Out of scope (this version): online payment gateways, SMS/email notification "
       "delivery, native mobile clients, cloud deployment.")

H("1.2 Definitions, Acronyms, Abbreviations", 2)
table(["Term", "Meaning"], [
 ("USAMIS", "University Student Academic Management Information System"),
 ("MIS", "Management Information System"),
 ("SRS","Software Requirements Specification (requirements input document)"),
 ("RBAC", "Role-Based Access Control"),
 ("FK","Foreign Key"),
 ("3NF","Third Normal Form"),
 ("FR / NFR","Functional / Non-Functional Requirement"),
 ("BCrypt", "Adaptive password-hashing function (Blowfish-based)"),
 ("SPA","Single-Page Application"),
 ("MLP / LSTM","Multilayer Perceptron / Long Short-Term Memory (neural networks)"),
 ("KPI","Key Performance Indicator"),
])

# ============================================================ 2. OVERVIEW
doc.add_page_break()
H("2. System Overview", 1)
P("USAMIS runs as a single Java web application on one public port. A Python AI "
  "micro-service runs on loopback and is reached only through the Java backend, so the "
  "user-facing surface remains a single port. This was verified live (Section 12).")
table(["Attribute", "Value"], [
 ("Architecture", "Layered (presentation / servlet / DAO / database)"),
 ("Public port", "8080 (context /usamis)"),
 ("AI service", "127.0.0.1:8099, loopback-only, token-gated"),
 ("Database", "PostgreSQL 17, database usamis"),
 ("Tables / Views", "15 tables + 3 computed views"),
 ("Foreign keys", "21"),
 ("Servlets / URL patterns", "14 servlets / 32 patterns"),
 ("Roles / Permissions", "5 roles / 13 permissions"),
 ("Modules", "M-01 … M-10 (M-10 = AI)"),
])
figure(os.path.join(DIAG,"F1_system_architecture.png"), "Figure 1 — System architecture (single public port).")
figure(os.path.join(DIAG,"F2_deployment.png"), "Figure 2 — Deployment view.", width=5.6)

# ============================================================ 3. REQUIREMENTS
doc.add_page_break()
H("3. Requirements Specification", 1)
P("The requirements were provided by the requirements engineer in the SRS. Functional "
  "requirements are grouped by module (M-01 … M-10). The AI capability (M-10) "
  "corresponds to the 'AI-Powered Student Performance Prediction' recommendation in the "
  "MIS investigation; it is implemented and therefore formalised here as a first-class "
  "module (FR-31 … FR-34).")

H("3.1 Functional Requirements", 2)
table(["ID", "Requirement", "Module"], [
 ("FR-01","Users authenticate with a username and password; sessions are created server-side.","M-01"),
 ("FR-02","Repeated failed logins are throttled (5 attempts / 15-minute window).","M-01"),
 ("FR-03","Sessions use an HttpOnly cookie with an expiry; unauthenticated API calls return 401.","M-01"),
 ("FR-04","New students may self-register with department/program selection.","M-01"),
 ("FR-05","Every API request re-checks the caller's role permissions (RBAC).","M-01"),
 ("FR-06","Admins manage users: create, edit, deactivate, assign roles.","M-02"),
 ("FR-07","The permission matrix (role × permission) is viewable by admins.","M-02"),
 ("FR-08","Registrar/admin register students with unique student IDs.","M-03"),
 ("FR-09","Student records can be searched (partial/ILIKE) and filtered.","M-03"),
 ("FR-10","Student records can be updated; deletion is a soft de-activation.","M-03"),
 ("FR-11","A list of at-risk students (GPA < 2.5) is available.","M-03"),
 ("FR-12","Courses are created with code, title, credits and a capacity.","M-04"),
 ("FR-13","Course capacity is tracked as enrollments accumulate.","M-04"),
 ("FR-14","Students enroll in courses for a semester; duplicates are rejected.","M-05"),
 ("FR-15","A full course rejects further enrollment with a clear error.","M-05"),
 ("FR-16","Students may drop an enrollment; the seat is released.","M-05"),
 ("FR-17","Lecturers/admin enter a numeric score for an enrollment.","M-06"),
 ("FR-18","Letter grade and GPA points are computed automatically from the score.","M-06"),
 ("FR-19","A student transcript (all graded courses + cumulative GPA) is produced.","M-06"),
 ("FR-20","Grade updates recompute GPA; prior values are not silently lost.","M-06"),
 ("FR-21","Fee records are created per student per semester.","M-07"),
 ("FR-22","Payments are recorded against a fee record; balance is tracked.","M-07"),
 ("FR-23","A defaulters list (outstanding balance) is available to finance/admin.","M-07"),
 ("FR-24","Role-aware dashboard KPIs are generated live from the database.","M-08"),
 ("FR-25","Management reports (distribution, pass rate, collection) are generated.","M-08"),
 ("FR-26","All write actions and logins are written to an append-only audit log.","M-09"),
 ("FR-27","Access denials are recorded in the audit log.","M-09"),
 ("FR-28","The audit log is queryable (paginated) by admins.","M-09"),
 ("FR-29","A student's performance-risk prediction is produced by the AI service.","M-10"),
 ("FR-30","The AI service exposes a health/status endpoint.","M-10"),
 ("FR-31","Enrollment demand is forecast for upcoming semesters.","M-10"),
 ("FR-32","Course recommendations are produced for a student.","M-10"),
 ("FR-33","AI access is permission-gated (VIEW_AI / MANAGE_AI).","M-10"),
 ("FR-34","AI service failure degrades gracefully (503) without affecting the rest of the app.","M-10"),
])

H("3.2 Non-Functional Requirements", 2)
table(["ID", "Category", "Requirement"], [
 ("NFR-01","Performance","Typical API reads respond in < 500 ms on dev hardware."),
 ("NFR-02","Scalability","Schema supports thousands of students/enrollments via indexed access."),
 ("NFR-03","Security","Passwords hashed with BCrypt (cost 12); no plaintext storage."),
 ("NFR-04","Security","SQL injection prevented via parameterized statements."),
 ("NFR-05","Security","Least privilege: 5 roles, 13 permissions, per-request checks."),
 ("NFR-06","Reliability","AI outage degrades gracefully; core functions unaffected."),
 ("NFR-07","Usability","Single-page UI, role-aware navigation, responsive layout."),
 ("NFR-08","Maintainability","Layered design; DAOs isolate SQL from servlets."),
 ("NFR-09","Auditability","Immutable append-only audit trail of actions and denials."),
 ("NFR-10","Portability","Single host; configurable via properties files."),
])

# ============================================================ 4. ARCHITECTURE
doc.add_page_break()
H("4. System Architecture & Design", 1)
H("4.1 Architectural Style", 2)
P("A classic layered architecture: the browser talks to servlets (controllers); servlets "
  "delegate to Data Access Objects (DAOs) which own all SQL; the database stores the "
  "normalised data. Cross-cutting concerns (authentication, RBAC, audit) are handled by "
  "an AuthFilter and shared utilities rather than scattered through controllers.")

H("4.2 Component Model", 2)
figure(os.path.join(DIAG,"F12_class_layers.png"), "Figure 3 — Class/layer diagram (servlets → DAOs → connection).")

H("4.3 Use Cases / Context", 2)
figure(os.path.join(DIAG,"F3_use_case.png"), "Figure 4 — Use-case/context diagram (5 roles, 10 modules).")

H("4.4 Screen / Navigation Map", 2)
figure(os.path.join(DIAG,"F6_screen_map.png"), "Figure 5 — Screen and navigation map.", width=5.6)

# ============================================================ 5. DATA
doc.add_page_break()
H("5. Data Design", 1)
H("5.1 Entity-Relationship Model", 2)
P("The schema is normalised to Third Normal Form. Departments, programs, roles and "
  "instructors are stored once and referenced by foreign keys, eliminating the "
  "transitive duplication found in spreadsheet-based systems.")
figure(os.path.join(DIAG,"F4_erd.png"), "Figure 6 — Entity-relationship diagram (15 tables).", width=6.4)

H("5.2 Integrity Constraints", 2)
bullet("21 foreign-key constraints enforce referential integrity (verified).")
bullet("UNIQUE(enrollment_id) on grades guarantees one grade per enrollment.")
bullet("UNIQUE(student_id, course_id, semester) on enrollments prevents duplicates.")
bullet("CHECK constraints bound scores and monetary values.")

H("5.3 Computed Views", 2)
table(["View", "Purpose"], [
 ("v_student_gpa","Per-student GPA computed live from grades (never stored redundantly)."),
 ("v_fee_summary","Per-student fee totals, paid, and outstanding balance."),
 ("v_course_enrollment","Enrolled vs capacity per course."),
])

H("5.4 Information / Decision Pipeline", 2)
figure(os.path.join(DIAG,"F9_data_pipeline.png"), "Figure 7 — Raw data → normalised → information → decisions.", width=6.4)

# ============================================================ 6. AI
doc.add_page_break()
H("6. AI Decision Support (Module M-10)", 1)
P("The AI capability is provided by an isolated Python micro-service reached only "
  "through the Java backend. It is token-gated and fails closed: if its token is not "
  "configured, or the service is down, AI endpoints return 503 while the rest of the "
  "application continues to work normally.")

H("6.1 Model Pipeline", 2)
figure(os.path.join(DIAG,"F13_ai_pipeline.png"), "Figure 8 — AI model pipeline and real artifacts.", width=6.0)

H("6.2 Models and Verified Metrics", 2)
P("The following figures are read directly from the deployed model metadata files "
  "(trained on a synthetic generator; this is disclosed, not hidden).")
m = MLP["metrics"]
table(["Model", "Task", "Metric", "Value"], [
 ("mlp_performance","Performance score + risk band","MAE", m["mae"]),
 ("mlp_performance","","RMSE", m["rmse"]),
 ("mlp_performance","","R²", m["r2"]),
 ("mlp_performance","","Risk precision", m["risk_precision"]),
 ("mlp_performance","","Risk recall", m["risk_recall"]),
 ("mlp_performance","","Risk F1", m["risk_f1"]),
 ("mlp_performance","","Best epoch / val size", f"{m['best_epoch']} / {m['val_size']}"),
 ("lstm_enrollment","Enrollment forecast","val RMSE (scaled)", LSTM["metrics"]["val_rmse_scaled"]),
])
P("Risk threshold = 60 (score). Trained on: “" + MLP.get("trained_on","synthetic") + "” data.", italic=True)

H("6.3 Feature Set", 2)
for f in MLP["feature_names"]:
    bullet(f)

H("6.4 Sequence of an AI Prediction", 2)
figure(os.path.join(DIAG,"F8_seq_ai_predict.png"), "Figure 9 — Sequence: AI performance prediction (Java → AI service).", width=6.4)

# ============================================================ 7. SECURITY
doc.add_page_break()
H("7. Security Design", 1)
P("Security is layered ('defence in depth'). Each layer assumes the one above it may "
  "fail.")
table(["Layer", "Mechanism", "Verified"], [
 ("Input","Server-side validation; parameterized SQL","Yes"),
 ("Application","AuthFilter on /api/*; per-servlet RBAC","Yes"),
 ("Database","FK / UNIQUE / CHECK constraints","21 FKs"),
 ("Credentials","BCrypt hashing, cost 12","Yes"),
 ("Session","HttpOnly, SameSite=Strict, 60-min TTL; 401 without session","Yes"),
 ("Audit","Append-only log; ACCESS_DENIED recorded","Yes"),
 ("Anti-IDOR","Students self-scoped on every endpoint","Yes"),
 ("AI isolation","Loopback-only, token-gated, fail-closed 503","Yes"),
])
H("7.1 RBAC Model", 2)
figure(os.path.join(DIAG,"F10_rbac_matrix.png"), "Figure 10 — RBAC: roles mapped to permissions.", width=6.2)
H("7.2 Request Lifecycle (with audit)", 2)
figure(os.path.join(DIAG,"F11_request_lifecycle.png"), "Figure 11 — Request lifecycle and audit capture.", width=6.0)

# ============================================================ 8. FLOWS
doc.add_page_break()
H("8. Process & Data Flows", 1)
figure(os.path.join(DIAG,"F5_dfd_level1.png"), "Figure 12 — Level-1 data-flow diagram.", width=6.2)
figure(os.path.join(DIAG,"F7_seq_login.png"), "Figure 13 — Sequence: login (bcrypt verification).", width=6.2)

# ============================================================ 9. IMPLEMENTATION
doc.add_page_break()
H("9. Implementation", 1)
P("The system is implemented in Java (Jakarta Servlet API on embedded Tomcat) with a "
  "vanilla single-page front-end served as a static resource, and a Python FastAPI "
  "micro-service for AI.")
table(["Area", "Technology"], [
 ("Backend","Java (Servlets 6 / Jakarta), embedded Tomcat"),
 ("Front-end","HTML/CSS/JS single-page app (java/src/main/webapp/index.html)"),
 ("Database","PostgreSQL 17 via JDBC (parameterized)"),
 ("AI","Python 3.11+, FastAPI, PyTorch"),
 ("Build/Docs","javac; python-docx / python-pptx / matplotlib for docs & diagrams"),
])
H("9.1 API Surface", 2)
table(["Servlet", "URL patterns"], [
 ("LoginServlet","/api/auth/login, /api/auth/logout, /api/auth/me"),
 ("RegisterServlet","/api/auth/register"),
 ("LookupServlet","/api/lookups/departments, /api/lookups/programs"),
 ("HealthServlet","/api/health"),
 ("StudentServlet","/api/students, /api/students/*"),
 ("CourseServlet","/api/courses, /api/courses/*"),
 ("EnrollmentServlet","/api/enrollments, /api/enrollments/*"),
 ("GradeServlet","/api/grades, /api/grades/*"),
 ("FeeServlet","/api/fees, /api/fees/*"),
 ("DashboardServlet","/api/dashboard"),
 ("PermissionServlet","/api/permissions, /api/permissions/mine"),
 ("UserServlet","/api/users, /api/users/*"),
 ("AuditServlet","/api/audit, /api/audit/*"),
 ("AiServlet","/api/ai/status, /api/ai/me, /api/ai/insights, /api/ai/insights/*, "
  "/api/ai/student/*, /api/ai/predict/*, /api/ai/forecast/*, /api/ai/recommend/*"),
])

# ============================================================ 10. TESTING
doc.add_page_break()
H("10. Testing & Verification", 1)
P("Verification was performed by running the system, not by inspection. The full log is "
  "in docs/QA_Verification_Log.md; the summary is below.")
table(["Suite", "Command", "Result"], [
 ("API regression","tools/smoke-test.ps1","23 / 23 PASS"),
 ("AI end-to-end","tools/verify-ai.py","19 / 19 PASS"),
 ("AI unit/service","pytest ai-service/tests","14 / 14 PASS"),
 ("Total","","56 / 56 PASS"),
])
H("10.1 RBAC Matrix (live)", 2)
table(["Endpoint","admin","registrar","lecturer","finance","student"], [
 ("/dashboard","200","200","200","200","200"),
 ("/students","200","200","403","403","200 (self)"),
 ("/courses","200","200","200","200","200"),
 ("/enrollments","200","200","200","200","200 (self)"),
 ("/grades","200","200","200","403","200 (self)"),
 ("/fees","200","403","403","200","200 (self)"),
 ("/users","200","403","403","403","403"),
 ("/audit","200","403","403","403","403"),
 ("/ai/status","200","403","403","403","403"),
 ("/ai/insights","200","200","200","200","403"),
])
H("10.2 Security Probes", 2)
table(["Probe","Expected","Actual","Verdict"], [
 ("Anon GET /students","401","401","PASS"),
 ("Student GET /students/1 (other)","no leak","403","PASS"),
 ("Student GET /enrollments?student=1","self only","200, own rows","PASS"),
 ("Student GET /audit","403","403","PASS"),
 ("AI service, no token","401","401","PASS"),
 ("AI service, no token configured","503 (fail-closed)","503","PASS"),
])
H("10.3 Observed Data State", 2)
table(["Table","Rows"], [
 ("students","63"), ("courses","13"), ("enrollments","156"), ("grades","156"),
 ("fee_records","52"), ("audit_log","530+ (append-only)"),
 ("ai_prediction","3"), ("users","13"), ("attendance","0 (schema present)"),
])

# ============================================================ 11. LIMITATIONS
H("11. Limitations & Honest Disclosure", 1)
bullet("AI models are trained on a SYNTHETIC generator; metrics are honest on that "
       "generator but do not represent real JIT data.")
bullet("No ROC / PR / confusion-matrix artifacts were saved by training, so none are "
       "reported here; only the real scalar metrics above are shown.")
bullet("Attendance table exists but is unpopulated (no entry UI in v1.0).")
bullet("Performance / load targets are specified (NFR-01/02) but not empirically "
       "benchmarked in this report.")
bullet("HTTPS/Secure cookie not exercised in the dev (HTTP) configuration.")
bullet("Screenshots of the running UI could not be captured reliably in the build "
       "environment; UI behaviour was verified via the live DOM (Section 10) instead.")

# ============================================================ 12. CONCLUSION
doc.add_page_break()
H("12. Conclusion & Evidence Summary", 1)
P("USAMIS satisfies the definition of a Management Information System and meets the "
  "requirements supplied by the requirements engineer, extended by the AI module (M-10). "
  "All 56 automated checks pass; per-role RBAC and anti-IDOR behaviour were verified "
  "live; the schema compiles and the single-port AI integration works end to end and "
  "degrades gracefully.")
table(["Item","Status"], [
 ("Build (Java compile)","clean"),
 ("Automated tests","56 / 56 PASS"),
 ("RBAC (per role)","verified"),
 ("IDOR probes","no leaks"),
 ("Single-port AI","verified"),
 ("Verdict","READY for course scope"),
])
P("Every figure in this report is traceable to a command that was run, a file in the "
  "repository, or a deployed model artifact. Where the requirements and the built "
  "system diverged, the divergence has been disclosed and reconciled.",
  italic=True)

doc.save(OUT)
print("Saved:", OUT)
print("Paragraphs:", len(doc.paragraphs), "Tables:", len(doc.tables))
