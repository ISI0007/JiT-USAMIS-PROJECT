"""Render the remaining USAMIS diagrams F11-F13 (matplotlib only, no Graphviz)."""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Rectangle

OUT = r"C:\Users\Admin\.openclaw\workspace\JiT-USAMIS-PROJECT\docs\diagrams"
NAVY="#12355b"; BLUE="#dbe9ff"; GREEN="#d9ffd0"; PINK="#ffd0e8"; YEL="#fff2cc"; TEAL="#d0f0f0"; GREY="#f0f0f0"
FONT="DejaVu Sans"

def newfig(w=12,h=6):
    f,a=plt.subplots(figsize=(w,h),dpi=140); a.set_xlim(0,100); a.set_ylim(0,100); a.axis("off"); return f,a
def box(a,x,y,w,h,t,fill=BLUE,fs=9.5,bold=False):
    a.add_patch(FancyBboxPatch((x,y),w,h,boxstyle="round,pad=0.6,rounding_size=1.2",
        lw=1.4,ec=NAVY,fc=fill))
    a.text(x+w/2,y+h/2,t,ha="center",va="center",fontsize=fs,fontfamily=FONT,
           fontweight="bold" if bold else "normal")
def arrow(a,x1,y1,x2,y2,l="",rad=0.0,fs=8,off=0):
    a.add_patch(FancyArrowPatch((x1,y1),(x2,y2),arrowstyle="-|>",mutation_scale=14,lw=1.4,color=NAVY,
        connectionstyle=f"arc3,rad={rad}"))
    if l: a.text((x1+x2)/2,(y1+y2)/2+off,l,ha="center",va="center",fontsize=fs,fontfamily=FONT,
                 bbox=dict(fc="white",ec="none",alpha=0.8,pad=0.5))
def title(a,t): a.text(50,97,t,ha="center",va="top",fontsize=15,fontweight="bold",color=NAVY,fontfamily=FONT)
def save(f,n): f.savefig(os.path.join(OUT,n+".png"),bbox_inches="tight",facecolor="white"); plt.close(f); print(n+".png OK")

# F11 state
f,a=newfig(13,5); title(a,"F11 — State / Request Lifecycle (with audit)")
nodes=[("Session\nestablished",TEAL,3),("Request",BLUE,22),("Authorized?",YEL,41),
 ("200 / 201",GREEN,62),("401 / 403",PINK,62),("audit_log\nrow",GREY,83)]
box(a,3,42,15,16,nodes[0][0],nodes[0][1]); box(a,22,42,15,16,nodes[1][0],nodes[1][1])
box(a,41,42,15,16,nodes[2][0],nodes[2][1]); box(a,62,58,18,14,nodes[3][0],nodes[3][1])
box(a,62,26,18,14,nodes[4][0],nodes[4][1]); box(a,83,42,15,16,nodes[5][0],nodes[5][1])
arrow(a,18,50,22,50); arrow(a,37,50,41,50); arrow(a,56,50,62,65,"yes",off=2)
arrow(a,56,50,62,33,"no",off=-2); arrow(a,80,65,83,55); arrow(a,80,33,83,45)
save(f,"F11_request_lifecycle")

# F12 class
f,a=newfig(14,8); title(a,"F12 — Class / Layer Diagram (real servlets -> DAOs)")
servs=["LoginServlet","RegisterServlet","UserServlet","StudentServlet","CourseServlet",
 "EnrollmentServlet","GradeServlet","FeeServlet","DashboardServlet","AuditServlet","AiServlet"]
for i,s in enumerate(servs):
    x=2+(i%4)*24; y=80-(i//4)*13
    box(a,x,y,21,9,s,BLUE,8.5,bold=(s=="AiServlet"))
daos=["UserDAO","StudentDAO","CourseDAO","EnrollmentDAO","GradeDAO","FeeDAO","DashboardDAO","AuditDAO","AiPredictionDAO","AiClient","AiService"]
for i,d in enumerate(daos):
    x=2+(i%4)*24; y=30-(i//4)*13
    box(a,x,y,21,9,d,GREEN if d not in("AiClient","AiService") else YEL,8.5,bold=(d in("AiClient","AiService")))
box(a,30,3,40,9,"DatabaseConnection (JDBC pool)",GREY,9)
save(f,"F12_class_layers")

# F13 AI pipeline
f,a=newfig(13,6); title(a,"F13 — AI Model Pipeline (real artifacts)")
box(a,6,66,24,14,"synthetic.py\ngenerate training data",YEL,9.5)
box(a,40,66,30,14,"train_models.py\n(PyTorch)",BLUE,9.5)
box(a,6,34,30,14,"mlp_performance.pt\nR2 0.6709 | recall 0.8741",GREEN,9)
box(a,44,34,34,14,"lstm_enrollment.pt\nval RMSE 0.2497 (scaled)",GREEN,9)
box(a,28,6,44,14,"FastAPI serve :8099  (predict / forecast / recommend / train)",PINK,9)
arrow(a,18,66,18,48); arrow(a,30,73,40,73); arrow(a,55,66,21,48) if False else None
arrow(a,55,66,58,48); arrow(a,21,34,40,20); arrow(a,61,34,60,20)
save(f,"F13_ai_pipeline")
print("done")
