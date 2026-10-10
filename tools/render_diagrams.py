"""
Render USAMIS engineering diagrams F1-F13 to PNG using matplotlib only
(no Graphviz binary required). All content is sourced from the real repo:
 - 14 servlets / 32 URL patterns
 - 15 tables / 3 views / 21 FKs
 - ports 8080 (app) / 8099 (AI) / 5432 (PostgreSQL)
 - real model metrics from D:\\usamis-ai\\models\\*.json
"""
import os, json, textwrap
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Rectangle, Ellipse

OUT = r"C:\Users\Admin\.openclaw\workspace\JiT-USAMIS-PROJECT\docs\diagrams"
os.makedirs(OUT, exist_ok=True)

NAVY="#12355b"; BLUE="#dbe9ff"; GREEN="#d9ffd0"; PINK="#ffd0e8"; YEL="#fff2cc"
PURP="#e0d0ff"; GREY="#f0f0f0"; TEAL="#d0f0f0"
FONT="DejaVu Sans"

def newfig(w=13,h=8):
    f,a=plt.subplots(figsize=(w,h),dpi=140); a.set_xlim(0,100); a.set_ylim(0,100)
    a.axis("off"); return f,a

def box(a,x,y,w,h,text,fill=BLUE,fs=10,bold=False,align="center"):
    a.add_patch(FancyBboxPatch((x,y),w,h,boxstyle="round,pad=0.6,rounding_size=1.2",
        linewidth=1.4,edgecolor=NAVY,facecolor=fill))
    a.text(x+w/2,y+h/2,text,ha="center",va="center",fontsize=fs,wrap=True,
           fontfamily=FONT,fontweight="bold" if bold else "normal")

def cyl(a,x,y,w,h,text,fill=GREY,fs=10):
    a.add_patch(FancyBboxPatch((x,y+h*0.12),w,h*0.76,boxstyle="round,pad=0.3",
        linewidth=1.4,edgecolor=NAVY,facecolor=fill))
    a.add_patch(Ellipse((x+w/2,y+h*0.88),w,h*0.24,linewidth=1.4,edgecolor=NAVY,facecolor=fill))
    a.text(x+w/2,y+h*0.45,text,ha="center",va="center",fontsize=fs,fontfamily=FONT)

def arrow(a,x1,y1,x2,y2,label="",style="-|>",color=NAVY,rad=0.0,fs=8,off=0):
    a.add_patch(FancyArrowPatch((x1,y1),(x2,y2),arrowstyle=style,mutation_scale=14,
        linewidth=1.4,color=color,connectionstyle=f"arc3,rad={rad}"))
    if label:
        a.text((x1+x2)/2,(y1+y2)/2+off,label,ha="center",va="center",fontsize=fs,
               color="#333",fontfamily=FONT,bbox=dict(fc="white",ec="none",alpha=0.75,pad=0.6))

def title(a,t):
    a.text(50,97,t,ha="center",va="top",fontsize=15,fontweight="bold",color=NAVY,fontfamily=FONT)

def save(f,name):
    f.savefig(os.path.join(OUT,name+".png"),bbox_inches="tight",facecolor="white")
    plt.close(f); print(name+".png OK")

# ---------------- F1 architecture ----------------
f,a=newfig(); title(a,"F1 — System Architecture (single public port)")
box(a,32,84,36,9,"Web Browser\nSPA  index.html",BLUE,11,bold=True)
a.add_patch(Rectangle((6,40),88,38,fill=False,ls="--",ec="#2b5c9b",lw=1.4))
a.text(9,76,"Java Backend — Jakarta Servlet + embedded Tomcat  :8080",fontsize=10,color="#2b5c9b",fontweight="bold",fontfamily=FONT)
box(a,9,66,24,7,"AuthFilter\n/api/* session + RBAC",TEAL,9)
box(a,37,66,26,7,"14 Servlets\n32 URL patterns",BLUE,9)
box(a,67,66,24,7,"DAO / Service\nPreparedStatement SQL",GREEN,9)
box(a,37,53,26,7,"AiClient (HTTP + token)",YEL,9)
a.add_patch(Rectangle((6,6),88,30,fill=False,ls="--",ec="#9b2b6b",lw=1.4))
a.text(9,33,"AI Micro-service — FastAPI  :8099 loopback-only",fontsize=10,color="#9b2b6b",fontweight="bold",fontfamily=FONT)
box(a,12,20,30,8,"Predict / Forecast /\nRecommend / Train",PINK,9)
box(a,58,20,30,8,"mlp_performance\nlstm_enrollment\ngraph recommender",PURP,8)
cyl(a,12,7,30,10,"PostgreSQL 17\nusamis: 15 tables + 3 views")
arrow(a,50,84,50,73," /api/* "); arrow(a,21,66,37,69); arrow(a,63,69,67,69)
arrow(a,50,66,50,60,off=2); arrow(a,50,53,42,28," /api/ai/* ",rad=0.0,off=3)
arrow(a,27,20,50,20,"X-AI-Service-Token",off=1.5); arrow(a,73,20,73,17)
save(f,"F1_system_architecture")

# ---------------- F2 deployment ----------------
f,a=newfig(12,6); title(a,"F2 — Deployment View")
box(a,4,55,20,12,"User\n(5 roles)",BLUE,10,bold=True)
a.add_patch(Rectangle((32,15),60,70,fill=False,ls="--",ec="#555",lw=1.3))
a.text(34,78,"Single host (dev)",fontsize=10,color="#555",fontweight="bold",fontfamily=FONT)
box(a,38,60,46,10,"Port 8080 — Java app (public)",GREEN,10)
box(a,38,42,46,10,"Port 8099 — AI service (loopback)",PINK,10)
box(a,38,24,46,10,"Port 5432 — PostgreSQL 17",GREY,10)
arrow(a,24,61,38,65," HTTP "); arrow(a,61,60,61,52," internal "); arrow(a,61,42,61,34," JDBC ")
save(f,"F2_deployment")

# ---------------- F3 use case ----------------
f,a=newfig(12,9); title(a,"F3 — Use-Case / Context Diagram")
roles=[("Admin",YEL,88),("Registrar",BLUE,70),("Lecturer",GREEN,52),("Finance",PINK,34),("Student",PURP,16)]
for n,c,y in roles: box(a,3,y,15,10,n,c,10,bold=True)
ucs=["Authenticate (M-01)","Manage students (M-03)","Manage courses (M-04)","Enroll/drop (M-05)",
     "Enter grades (M-06)","Manage fees (M-07)","View reports (M-08)","Audit log (M-09)","AI decision support (M-10)"]
yy=[82,73,64,55,46,37,28,19,10]
for t,y in zip(ucs,yy):
    a.add_patch(Ellipse((62,y+3),52,7,facecolor="#eef3fb",edgecolor=NAVY,lw=1.2))
    a.text(62,y+3,t,ha="center",va="center",fontsize=8.5,fontfamily=FONT)
arrow(a,18,93,36,85); arrow(a,18,89,36,76); arrow(a,18,85,36,67); arrow(a,18,81,36,58)
arrow(a,18,75,36,76); arrow(a,18,71,36,67); arrow(a,18,67,36,58); arrow(a,18,63,36,31)
arrow(a,18,57,36,49); arrow(a,18,53,36,40); arrow(a,18,44,36,31)
arrow(a,18,22,36,31)
save(f,"F3_use_case")

# ---------------- F4 ERD ----------------
f,a=newfig(15,10); title(a,"F4 — Entity-Relationship Diagram (15 tables)")
tables={
 "roles":(3,84),("permissions" if False else "permissions"):(3,66),"role_permissions":(3,52),
 "users":(25,84),"departments":(25,66),"programs":(25,52),
 "students":(47,80),"instructors":(47,62),"courses":(47,44),
 "enrollments":(69,70),"grades":(69,52),"attendance":(69,34),
 "fee_records":(88,70),"audit_log":(88,52),
}
cols={"roles":["id (PK)","name","description"],
 "permissions":["id (PK)","name","module"],
 "role_permissions":["role_id (PK,FK)","permission_id (PK,FK)"],
 "users":["id (PK)","username","password_hash","role_id (FK)","status"],
 "departments":["id (PK)","name","code"],
 "programs":["id (PK)","name","department_id (FK)"],
 "students":["id (PK)","student_id","name","department_id (FK)","program_id (FK)","user_id (FK)"],
 "instructors":["id (PK)","name","department_id (FK)"],
 "courses":["id (PK)","code","credits","max_enrollment","department_id (FK)","instructor_id (FK)"],
 "enrollments":["id (PK)","student_id (FK)","course_id (FK)","semester","status"],
 "grades":["id (PK)","enrollment_id (FK,UQ)","score","letter","gpa_points","entered_by (FK)"],
 "attendance":["id (PK)","enrollment_id (FK)","date","present"],
 "fee_records":["id (PK)","student_id (FK)","semester","total","paid","status"],
 "audit_log":["id (PK)","user_id (FK)","action","entity","created_at"]}
pos={"roles":(3,82),"permissions":(3,64),"role_permissions":(3,50),"users":(24,82),
 "departments":(24,64),"programs":(24,50),"students":(45,78),"instructors":(45,60),
 "courses":(45,42),"enrollments":(66,68),"grades":(66,50),"attendance":(66,33),
 "fee_records":(85,68),"audit_log":(85,50)}
centers={}
for t,(x,y) in pos.items():
    rows=cols[t]; h=4+3*len(rows)
    a.add_patch(Rectangle((x,y),20,h,facecolor="#f7f9fc",edgecolor=NAVY,lw=1.3))
    a.add_patch(Rectangle((x,y+h-4.2),20,4.2,facecolor="#dbe9ff",edgecolor=NAVY,lw=1.3))
    a.text(x+10,y+h-2.1,t,ha="center",va="center",fontsize=8.5,fontweight="bold",fontfamily=FONT)
    for i,row in enumerate(rows):
        a.text(x+1.2,y+h-6.4-3*i,row,ha="left",va="center",fontsize=7.2,fontfamily="DejaVu Sans Mono")
    centers[t]=(x+10,y+h/2)
def link(t1,t2,off=0):
    (x1,y1),(x2,y2)=centers[t1],centers[t2]
    arrow(a,x1,y1,x2,y2,"",style="-",off=off)
for p in [("roles","role_permissions"),("permissions","role_permissions"),("roles","users"),
 ("users","students"),("departments","programs"),("departments","students"),
 ("departments","instructors"),("departments","courses"),("programs","students"),
 ("students","enrollments"),("courses","enrollments"),("enrollments","grades"),
 ("enrollments","attendance"),("students","fee_records"),("users","audit_log")]:
    link(*p)
save(f,"F4_erd")

# ---------------- F5 DFD ----------------
f,a=newfig(13,8); title(a,"F5 — Data Flow Diagram (Level 1)")
box(a,4,80,20,8,"Users\n(5 roles)",BLUE,10,bold=True)
procs=[("1.0 Authenticate\n& Authorize",72),("2.0 Manage\nStudents/Courses",55),
 ("3.0 Enrollment\n& Grades",38),("4.0 Fees",21),("5.0 Reports &\nAI Support",4)]
for t,y in procs: box(a,34,y,26,11,t,TEAL,9)
cyl(a,78,74,18,14,"users / roles"); cyl(a,78,55,18,12,"students / courses")
cyl(a,78,37,18,12,"enrollments/grades"); cyl(a,78,19,18,11,"fees / AI")
box(a,4,30,20,8,"6.0 Audit",PINK,9)
arrow(a,24,84,34,78); arrow(a,24,82,34,60); arrow(a,24,78,34,43); arrow(a,24,70,34,26)
arrow(a,60,78,78,80); arrow(a,60,60,78,60); arrow(a,60,43,78,43); arrow(a,60,26,78,25)
arrow(a,60,10,78,22)
save(f,"F5_dfd_level1")

# ---------------- F6 screen map ----------------
f,a=newfig(12,8); title(a,"F6 — Screen / Navigation Map")
box(a,40,86,20,8,"Login",YEL,11,bold=True)
box(a,40,70,20,8,"Dashboard\n(KPIs by role)",BLUE,10,bold=True)
tiles=["Students\n(M-03)","Courses\n(M-04)","Enrollments\n(M-05)","Grades\n(M-06)",
 "Fees\n(M-07)","Reports\n(M-08)","Audit Log\n(M-09)","AI Insights\n(M-10)","Users\n(M-02)"]
for i,t in enumerate(tiles):
    x=3+(i%3)*33; y=44-(i//3)*20
    box(a,x,y,28,14,t,[GREEN,PINK,BLUE][i%3],9.5)
    arrow(a,50,70,x+14,y+14,"")
save(f,"F6_screen_map")

# ---------------- F7 seq login ----------------
f,a=newfig(13,6.5); title(a,"F7 — Sequence: Login")
objs=["Browser","AuthFilter","LoginServlet","UserDAO","PostgreSQL"]
xs=[10,30,50,70,90]
for x,o in zip(xs,objs):
    box(a,x-7,84,14,7,o,BLUE if o!="PostgreSQL" else GREY,9.5,bold=True)
    a.plot([x,x],[10,84],ls=":",color="#999",lw=1)
steps=[(0,1,"POST /api/auth/login"),(1,2,"public path"),(2,3,"authenticate(u,p)"),
 (3,4,"SELECT ... WHERE username=?"),(4,3,"row (password_hash)"),
 (3,2,"BCrypt.checkpw() -> Optional<User>"),(2,0,"200 + Set-Cookie USAMIS_SESSION")]
y=78
for s,d,l in steps:
    arrow(a,xs[s],y,xs[d],y,l,off=2.2); y-=9.5
save(f,"F7_seq_login")

# ---------------- F8 seq ai ----------------
f,a=newfig(14,6.5); title(a,"F8 — Sequence: AI Performance Prediction")
objs=["Browser","AiServlet","AiClient","AI Service :8099","PostgreSQL"]
xs=[8,28,48,70,92]
for x,o in zip(xs,objs):
    box(a,x-8,84,16,7,o,PINK if "AI" in o else BLUE,9,bold=True)
    a.plot([x,x],[10,84],ls=":",color="#999",lw=1)
steps=[(0,1,"POST /api/ai/predict/{id}"),(1,4,"load features"),(4,1,"gpa, attendance, scores"),
 (1,2,"predict(features)"),(2,3,"POST /api/v1/predict/performance  X-AI-Service-Token"),
 (3,3,"MLP forward pass"),(3,2,"score, risk, band"),(1,4,"INSERT ai_prediction"),
 (1,0,"200 prediction")]
y=78
for s,d,l in steps:
    if s==d:
        a.add_patch(FancyArrowPatch((xs[s],y),(xs[s]+6,y),arrowstyle="-|>",mutation_scale=12,
            connectionstyle="arc3,rad=-1.2",color=NAVY,lw=1.2))
        a.text(xs[s]+7,y+2,l,fontsize=7.5,fontfamily=FONT)
    else:
        arrow(a,xs[s],y,xs[d],y,l,off=2.4,fs=7.5)
    y-=9.0
save(f,"F8_seq_ai_predict")

# ---------------- F9 pipeline ----------------
f,a=newfig(14,4.5); title(a,"F9 — Data / Information / Decision Pipeline")
box(a,2,30,16,30,"Raw academic data\n(scores, fees,\nenrollments)",YEL,9.5)
box(a,21,30,16,30,"Normalized tables\n(3NF, 15 tables)",BLUE,9.5)
box(a,40,30,16,30,"3 computed views\n(GPA, fees,\ncapacity)",TEAL,9.5)
box(a,59,30,16,30,"Information\n(KPIs, %, GPA,\nrisk band)",GREEN,9.5)
box(a,78,30,16,30,"Decisions\n(intervene,\nchase fees,\nschedule)",PINK,9.5)
for x in [18,37,56,75]: arrow(a,x,45,x+3,45)
save(f,"F9_data_pipeline")

# ---------------- F10 RBAC ----------------
f,a=newfig(12,8); title(a,"F10 — RBAC: Roles -> Permissions")
rroles=[("Admin",YEL,84),("Registrar",BLUE,66),("Lecturer",GREEN,48),("Finance",PINK,30),("Student",PURP,12)]
perms=["MANAGE_USERS (admin)","MANAGE_STUDENTS","MANAGE_COURSES","MANAGE_ENROLLMENTS",
 "ENTER_GRADES","MANAGE_FEES","VIEW_REPORTS","VIEW_AUDIT (admin)","VIEW_AI","MANAGE_AI (admin)"]
for n,c,y in rroles: box(a,4,y,18,11,n,c,10,bold=True)
for i,p in enumerate(perms):
    y=84-i*8.6
    a.add_patch(Rectangle((42,y),52,7,facecolor="#eef3fb",edgecolor=NAVY,lw=1.1))
    a.text(44,y+3.5,p,ha="left",va="center",fontsize=8.5,fontfamily=FONT)
map_={0:[0,1,2,3,4,5,6,7,8,9],1:[1,2,3,6,8],2:[4,8],3:[5,6],4:[6]}
for ri,plist in map_.items():
    for pi in plist: arrow(a,22,rroles[ri][2]+5.5,42,84-pi*8.6+3.5,"")
save(f,"F10_rbac_matrix")

print("done")
