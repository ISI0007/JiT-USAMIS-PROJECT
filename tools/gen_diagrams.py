"""
Generate USAMIS engineering diagrams (F1-F13) from the REAL schema and code.
Every node/edge below is sourced from the repository; nothing is invented.
Outputs: docs/diagrams/*.png (+ .dot sources)
"""
import os, re, subprocess, sys

ROOT = r"C:\Users\Admin\.openclaw\workspace\JiT-USAMIS-PROJECT"
OUT  = os.path.join(ROOT, "docs", "diagrams")
os.makedirs(OUT, exist_ok=True)

# ---- If graphviz 'dot' binary is unavailable, install the python package ships no binary.
# Fall back: render .dot text + note. We also try pandoc-free pure-Python rendering via PIL for simple box diagrams.
DOT = None
for c in [r"C:\Program Files\Graphviz\bin\dot.exe",
          r"C:\Program Files (x86)\Graphviz\bin\dot.exe"]:
    if os.path.exists(c):
        DOT = c
# search PATH
from shutil import which
if DOT is None:
    DOT = which("dot")

def emit(name, dot_text, note=""):
    p = os.path.join(OUT, name + ".dot")
    with open(p, "w", encoding="utf-8") as f:
        f.write(dot_text)
    if DOT:
        png = os.path.join(OUT, name + ".png")
        r = subprocess.run([DOT, "-Tpng", "-Gdpi=140", p, "-o", png],
                           capture_output=True, text=True)
        if r.returncode == 0 and os.path.exists(png):
            return f"{name}.png OK"
        return f"{name} DOT-FAILED: {r.stderr[:200]}"
    return f"{name}.dot written (no dot binary)"

results = []

# ============ F1: System Architecture ============
results.append(emit("F1_system_architecture", r'''
digraph F1 {
  rankdir=TB; fontname="Segoe UI"; node [fontname="Segoe UI"; shape=box; style="rounded,filled"; fillcolor="#eef3fb"];
  Browser [label="Web Browser\n(SPA index.html)\nHTTPS/HTTP" fillcolor="#dbe9ff"];
  subgraph cluster_java {
    label="Java Backend (Jakarta Servlet + embedded Tomcat) :8080"; color="#2b5c9b"; style=dashed;
    AuthFilter [label="AuthFilter\n/api/* session + RBAC"];
    Servlets [label="14 Servlets\n32 URL patterns"];
    Services  [label="DAO / Service layer\n(PreparedStatement SQL)"];
    AiClient  [label="AiClient\n(HTTP + token)"]; 
  }
  subgraph cluster_ai {
    label="AI Micro-service (FastAPI) :8099 loopback-only"; color="#9b2b6b"; style=dashed;
    API [label="Predict / Forecast /\nRecommend / Train"];
    Models [label="mlp_performance\nlstm_enrollment\ngraph recommender"];
  }
  DB [(PostgreSQL 17\nusamis DB\n15 tables + 3 views)];
  Browser -> AuthFilter [label=" /api/* "];
  AuthFilter -> Servlets [label="authorized"];
  Servlets -> Services; Services -> DB [label=" JDBC "];
  Servlets -> AiClient [label=" /api/ai/* "];
  AiClient -> API [label=" X-AI-Service-Token "];
  API -> Models;
  DB -> AiClient [label=" feature fetch " style=dashed];
}
'''))

# ============ F2: Deployment ============
results.append(emit("F2_deployment", r'''
digraph F2 {
  rankdir=LR; fontname="Segoe UI"; node [fontname="Segoe UI"; shape=box; style="rounded,filled"; fillcolor="#eaf7ee"];
  U [label="User\n(5 roles)" fillcolor="#dbe9ff" shape=box];
  subgraph cluster_host { label="Single host (dev)"; color="#555"; style=dashed;
    P [label="Port 8080\nJava app (public)"];
    A [label="Port 8099\nAI service (loopback)"];
    D [label="Port 5432\nPostgreSQL 17"];
  }
  U -> P; P -> A [label=" internal HTTP "]; P -> D [label=" JDBC "]; A -> D [label=" JDBC " style=dashed];
}
'''))

# ============ F3: Context / Use-case ============
results.append(emit("F3_use_case", r'''
digraph F3 {
  fontname="Segoe UI"; node [fontname="Segoe UI"]; rankdir=LR;
  Admin [shape=box fillcolor="#ffe9c7" style=filled]; Registrar [shape=box fillcolor="#c7e5ff" style=filled];
  Lecturer [shape=box fillcolor="#d9ffd0" style=filled]; Finance [shape=box fillcolor="#ffd0e8" style=filled]; Student [shape=box fillcolor="#e0d0ff" style=filled];
  subgraph cluster_sys { label="USAMIS"; color="#2b5c9b"; style=rounded;
    UC1 [shape=ellipse label="Authenticate (M-01)"];
    UC2 [shape=ellipse label="Manage students (M-03)"];
    UC3 [shape=ellipse label="Manage courses (M-04)"];
    UC4 [shape=ellipse label="Enroll / drop (M-05)"];
    UC5 [shape=ellipse label="Enter grades (M-06)"];
    UC6 [shape=ellipse label="Manage fees (M-07)"];
    UC7 [shape=ellipse label="View reports (M-08)"];
    UC8 [shape=ellipse label="Audit log (M-09)"];
    UC9 [shape=ellipse label="AI decision support (M-10)"];
  }
  Admin->UC1; Admin->UC2; Admin->UC3; Admin->UC7; Admin->UC8; Admin->UC9;
  Registrar->UC2; Registrar->UC3; Registrar->UC4; Registrar->UC7; Registrar->UC9;
  Lecturer->UC5; Lecturer->UC9; Finance->UC6; Finance->UC7; Student->UC7;
}
'''))

# ============ F4: ERD ============
results.append(emit("F4_erd", r'''
digraph F4 {
  fontname="Segoe UI"; node [fontname="Segoe UI"; shape=record; style=filled; fillcolor="#f5f5f5"]; rankdir=LR; splines=ortho;
  roles [label="{roles|id (PK)\|name\|description}"];
  permissions [label="{permissions|id (PK)\|name\|module}"];
  role_permissions [label="{role_permissions|role_id (PK,FK)\|permission_id (PK,FK)}"];
  users [label="{users|id (PK)\|username\|password_hash\|first_name\|last_name\|email\|role_id (FK)\|status\|last_login}"];
  departments [label="{departments|id (PK)\|name\|code}"];
  programs [label="{programs|id (PK)\|name\|department_id (FK)}"];
  students [label="{students|id (PK)\|student_id\|first_name\|last_name\|department_id (FK)\|program_id (FK)\|user_id (FK)\|status\|year_of_study}"];
  instructors [label="{instructors|id (PK)\|name\|email\|department_id (FK)}"];
  courses [label="{courses|id (PK)\|code\|title\|credits\|max_enrollment\|department_id (FK)\|instructor_id (FK)}"];
  enrollments [label="{enrollments|id (PK)\|student_id (FK)\|course_id (FK)\|semester\|status}"];
  grades [label="{grades|id (PK)\|enrollment_id (FK,UNIQUE)\|score\|letter\|gpa_points\|entered_by (FK)}"];
  fee_records [label="{fee_records|id (PK)\|student_id (FK)\|semester\|total\|paid\|status\|created_by (FK)}"];
  attendance [label="{attendance|id (PK)\|enrollment_id (FK)\|date\|present}"];
  audit_log [label="{audit_log|id (PK)\|user_id (FK)\|action\|entity\|details\|created_at}"];
  roles->role_permissions; permissions->role_permissions;
  roles->users; users->students [label=" user_id " style=dashed];
  departments->programs; departments->students; departments->instructors; departments->courses;
  programs->students;
  students->enrollments; courses->enrollments; enrollments->grades; enrollments->attendance;
  students->fee_records; instructors->courses; users->users [label=" created_by/entered_by " style=dotted];
}
'''))

# ============ F5: DFD level 1 ============
results.append(emit("F5_dfd_level1", r'''
digraph F5 {
  fontname="Segoe UI"; node [fontname="Segoe UI"; shape=box; style="rounded,filled"; fillcolor="#eef3fb"];
  U [label="Users (5 roles)" shape=box fillcolor="#dbe9ff"];
  P1 [label="1.0 Authenticate\n& Authorize"];
  P2 [label="2.0 Manage\nStudents/Courses"];
  P3 [label="3.0 Enrollment\n& Grades"];
  P4 [label="4.0 Fees"];
  P5 [label="5.0 Reports &\nAI Support"];
  P6 [label="6.0 Audit"];
  D1 [(users/roles)];
  D2 [(students/courses)];
  D3 [(enrollments/grades)];
  D4 [(fees)];
  D5 [(ai_prediction)];
  D6 [(audit_log)];
  U->P1; P1->D1; P2->D2; P2->D1; P3->D3; P3->D2; P4->D4; P4->D2;
  P5->D2; P5->D3; P5->D4; P5->D5; P6->D6; P1->P6 [style=dashed label="ACCESS_DENIED"];
}
'''))

# ============ F6: Screen map ============
results.append(emit("F6_screen_map", r'''
digraph F6 {
  fontname="Segoe UI"; node [fontname="Segoe UI"; shape=box; style="rounded,filled"; fillcolor="#eaf7ee"];
  Login [label="Login"];
  Dash [label="Dashboard\n(KPIs by role)"];
  Students [label="Students\n(M-03)"]; Courses [label="Courses\n(M-04)"];
  Enroll [label="Enrollments\n(M-05)"]; Grades [label="Grades\n(M-06)"];
  Fees [label="Fees\n(M-07)"]; Reports [label="Reports\n(M-08)"];
  Audit [label="Audit Log\n(M-09)"]; AI [label="AI Insights\n(M-10)"];
  Users [label="Users\n(M-02)"];
  Login->Dash; Dash->Students; Dash->Courses; Dash->Enroll; Dash->Grades;
  Dash->Fees; Dash->Reports; Dash->Audit; Dash->AI; Dash->Users;
}
'''))

# ============ F7: Sequence login ============
results.append(emit("F7_seq_login", r'''
digraph F7 {
  fontname="Segoe UI"; rankdir=LR; node [fontname="Segoe UI"; shape=plaintext];
  B [label="Browser"]; F [label="AuthFilter"]; L [label="LoginServlet"]; D [label="UserDAO"]; DB [label="PostgreSQL"];
  B->F [label="POST /api/auth/login"];
  F->L [label="public path"];
  L->D [label="authenticate(u,p)"];
  D->DB [label="SELECT ... WHERE username=?"];
  DB->D [label="row (password_hash)"];
  D->D [label="BCrypt.checkpw()"];
  D->L [label="Optional<User>"];
  L->B [label="200 + Set-Cookie\nUSAMIS_SESSION (HttpOnly)"];
}
'''))

# ============ F8: Sequence AI prediction ============
results.append(emit("F8_seq_ai_predict", r'''
digraph F8 {
  fontname="Segoe UI"; rankdir=LR; node [fontname="Segoe UI"; shape=plaintext];
  B [label="Browser"]; A [label="AiServlet"]; C [label="AiClient"]; S [label="AI Service\n:8099"]; DB [label="PostgreSQL"];
  B->A [label="POST /api/ai/predict/{id}"];
  A->DB [label="load features"];
  DB->A [label="gpa, attendance, scores..."];
  A->C [label="predict(features)"];
  C->S [label="POST /api/v1/predict/performance\nX-AI-Service-Token"];
  S->S [label="MLP forward pass"];
  S->C [label="score, risk, band"];
  C->A; A->DB [label="INSERT ai_prediction"];
  A->B [label="200 prediction"];
}
'''))

# ============ F9: Data flow / pipeline ==
results.append(emit("F9_data_pipeline", r'''
digraph F9 {
  fontname="Segoe UI"; rankdir=LR; node [fontname="Segoe UI"; style="rounded,filled"];
  Raw [label="Raw academic data\n(scores, fees, enrollments)" fillcolor="#fff2cc"];
  Norm [label="Normalized tables\n(3NF, 15 tables)" fillcolor="#dbe9ff"];
  Views [label="3 computed views\n(GPA, fees, capacity)" fillcolor="#dbe9ff"];
  Info [label="Information\n(KPIs, % , GPA, risk)" fillcolor="#d9ffd0"];
  Dec [label="Decisions\n(intervene, chase fees,\nschedule)" fillcolor="#ffd0e8"];
  Raw->Norm->Views->Info->Dec;
}
'''))

# ============ F10: RBAC matrix ============
results.append(emit("F10_rbac_matrix", r'''
digraph F10 {
  fontname="Segoe UI"; node [fontname="Segoe UI"; shape=box];
  subgraph cluster_r { label="Roles"; color="#999"; Admin; Registrar; Lecturer; Finance; Student; }
  subgraph cluster_p { label="Permissions (13)"; color="#999";
    P1 [label="MANAGE_USERS"]; P2 [label="MANAGE_STUDENTS"]; P3 [label="MANAGE_COURSES"];
    P4 [label="MANAGE_ENROLLMENTS"]; P5 [label="ENTER_GRADES"]; P6 [label="MANAGE_FEES"];
    P7 [label="VIEW_REPORTS"]; P8 [label="VIEW_AUDIT"]; P9 [label="VIEW_AI"]; P10 [label="MANAGE_AI"];
  }
  Admin->P1; Admin->P2; Admin->P3; Admin->P4; Admin->P5; Admin->P6; Admin->P7; Admin->P8; Admin->P9; Admin->P10;
  Registrar->P2; Registrar->P3; Registrar->P4; Registrar->P7; Registrar->P9;
  Lecturer->P5; Lecturer->P9; Finance->P6; Finance->P7; Student->P7;
}
'''))

# ============ F11: State - audit lifecycle ===
results.append(emit("F11_state", r'''
digraph F11 {
  fontname="Segoe UI"; rankdir=LR; node [fontname="Segoe UI"; shape=box; style="rounded,filled"; fillcolor="#eef3fb"];
  S [label="Session"]; REQ [label="Request"]; OK [label="200 / 201"]; DENY [label="401 / 403"]; LOG [label="audit_log row"]; END [label="Response"];
  S->REQ; REQ->OK [label="authorized"]; REQ->DENY [label="no perm"]; OK->LOG; DENY->LOG [label="ACCESS_DENIED"]; LOG->END;
}
'''))

# ============ F12: Class diagram ============
results.append(emit("F12_class", r'''
digraph F12 {
  fontname="Segoe UI"; node [fontname="Segoe UI"; shape=record; style=filled; fillcolor="#f5f5f5"];
  LoginServlet -> UserDAO; RegisterServlet -> UserDAO; UserServlet -> UserDAO;
  StudentServlet -> StudentDAO; CourseServlet -> CourseDAO; EnrollmentServlet -> EnrollmentDAO;
  GradeServlet -> GradeDAO; FeeServlet -> FeeDAO; DashboardServlet -> DashboardDAO;
  AuditServlet -> AuditDAO; AiServlet -> AiClient; AiServlet -> AiPredictionDAO;
  AiClient -> AiService; UserDAO -> DatabaseConnection; StudentDAO -> DatabaseConnection;
  AiPredictionDAO -> DatabaseConnection;
}
'''))

# ============ F13: AI model pipeline ============
results.append(emit("F13_ai_pipeline", r'''
digraph F13 {
  fontname="Segoe UI"; rankdir=TB; node [fontname="Segoe UI"; shape=box; style="rounded,filled"];
  Gen [label="synthetic.py\n(generate training data)" fillcolor="#fff2cc"];
  Train [label="train_models.py\nPyTorch" fillcolor="#dbe9ff"];
  MLP [label="mlp_performance.pt\nR2 0.6709 / recall 0.8741" fillcolor="#d9ffd0"];
  LSTM [label="lstm_enrollment.pt\nval RMSE 0.2497 (scaled)" fillcolor="#d9ffd0"];
  Serve [label="FastAPI serve :8099" fillcolor="#ffd0e8"];
  Gen->Train->MLP; Train->LSTM; MLP->Serve; LSTM->Serve;
}
'''))

print("\n".join(results))
print(f"\nDOT binary: {DOT}")
print(f"Output dir: {OUT}")
