"""Build the USAMIS presentation deck (16:9 PPTX) from real figures/metrics."""
import os, json
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN

ROOT = r"C:\Users\Admin\.openclaw\workspace\JiT-USAMIS-PROJECT"
DOCS = os.path.join(ROOT, "docs")
DIAG = os.path.join(DOCS, "diagrams")
OUT  = os.path.join(DOCS, "USAMIS_Presentation.pptx")

NAVY = RGBColor(0x12,0x35,0x5B); WHITE = RGBColor(0xFF,0xFF,0xFF)
GREY = RGBColor(0x55,0x55,0x55); LIGHT = RGBColor(0xEE,0xF3,0xFB)

def load(p):
    with open(p, encoding="utf-8") as f: return json.load(f)
MLP = load(r"D:\usamis-ai\models\mlp_performance.json")
LSTM = load(r"D:\usamis-ai\models\lstm_enrollment.json")

prs = Presentation()
prs.slide_width = Inches(13.333); prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]

def bg(slide, color=WHITE):
    slide.background.fill.solid(); slide.background.fill.fore_color.rgb = color

def title(slide, text, sub=None):
    tb = slide.shapes.add_textbox(Inches(0.6), Inches(0.35), Inches(12.1), Inches(1.0))
    tf = tb.text_frame; tf.word_wrap = True
    p = tf.paragraphs[0]; r = p.add_run(); r.text = text
    r.font.size = Pt(30); r.font.bold = True; r.font.color.rgb = NAVY
    if sub:
        p2 = tf.add_paragraph(); r2 = p2.add_run(); r2.text = sub
        r2.font.size = Pt(14); r2.font.color.rgb = GREY
    return tb

def bullets(slide, items, top=1.7, size=16):
    tb = slide.shapes.add_textbox(Inches(0.8), Inches(top), Inches(11.7), Inches(5.2))
    tf = tb.text_frame; tf.word_wrap = True
    for i, it in enumerate(items):
        p = tf.paragraphs[0] if i==0 else tf.add_paragraph()
        r = p.add_run(); r.text = "•  " + it
        r.font.size = Pt(size); r.font.color.rgb = RGBColor(0x22,0x22,0x22)
    return tb

def image(slide, path, top=1.7, height=None):
    if os.path.exists(path):
        kw = {}
        if height: kw["height"] = Inches(height)
        else: kw["width"] = Inches(11.0)
        slide.shapes.add_picture(path, Inches(1.15), Inches(top), **kw)

def table(slide, headers, rows, top=1.8, width=11.6, col_first=3.0):
    h = len(rows)+1; w = len(headers)
    sh = slide.shapes.add_table(h, w, Inches(0.8), Inches(top), Inches(width), Inches(0.4*h))
    tb = sh.table
    for j,hd in enumerate(headers):
        c = tb.cell(0,j); c.text = hd
        for p in c.text_frame.paragraphs:
            for r in p.runs: r.font.bold=True; r.font.size=Pt(12); r.font.color.rgb=WHITE
        c.fill.solid(); c.fill.fore_color.rgb = NAVY
    for i,row in enumerate(rows):
        for j,v in enumerate(row):
            c = tb.cell(i+1,j); c.text = str(v)
            for p in c.text_frame.paragraphs:
                for r in p.runs: r.font.size=Pt(11)
    return tb

# ---- Slide 1: title
s = prs.slides.add_slide(BLANK); bg(s, NAVY)
tb = s.shapes.add_textbox(Inches(0.8), Inches(2.2), Inches(11.7), Inches(2.5))
tf = tb.text_frame; tf.word_wrap=True
p=tf.paragraphs[0]; r=p.add_run(); r.text="USAMIS"
r.font.size=Pt(54); r.font.bold=True; r.font.color.rgb=WHITE
p=tf.add_paragraph(); r=p.add_run(); r.text="University Student Academic Management Information System"
r.font.size=Pt(20); r.font.color.rgb=RGBColor(0xCF,0xE0,0xF5)
p=tf.add_paragraph(); r=p.add_run(); r.text="Engineering Design & Implementation  ·  Jinling Institute of Technology"
r.font.size=Pt(14); r.font.color.rgb=RGBColor(0x9F,0xBF,0xE0)
p=tf.add_paragraph(); r=p.add_run()
r.text="Yaseen Hussain (2422486012)  ·  Supervisor: Prof. Li Wei  ·  Oct 2026"
r.font.size=Pt(13); r.font.color.rgb=RGBColor(0x9F,0xBF,0xE0)

# ---- Slide 2: problem / MIS framing
s = prs.slides.add_slide(BLANK); title(s, "Why an MIS, not a database", "Fragmented academic data → role-filtered management information")
bullets(s, [
 "Raw academic data (scores, fees, enrollments) is fragmented and hard to act on.",
 "USAMIS transforms it into information that supports decisions:",
 "   – early at-risk intervention (GPA < 2.5)",
 "   – fee-collection monitoring (62.5% collected, defaulters listed)",
 "   – course-capacity planning (enrolled vs maximum)",
 "   – accountability (append-only audit trail)",
 "Same data, different information per role (admin / registrar / lecturer / finance / student).",
])

# ---- Slide 3: architecture
s = prs.slides.add_slide(BLANK); title(s, "Architecture", "One public port; AI reached only through the backend")
image(s, os.path.join(DIAG,"F1_system_architecture.png"), top=1.7, height=5.2)

# ---- Slide 4: modules
s = prs.slides.add_slide(BLANK); title(s, "Modules M-01 … M-10", "10 modules, 34 functional requirements")
table(s, ["#","Module","Users"], [
 ("M-01","Authentication & Authorization","all"),
 ("M-02","User & Role Management","admin"),
 ("M-03","Student Records","admin, registrar"),
 ("M-03","Course Catalog → M-04","admin, registrar"),
 ("M-05","Enrollment","admin, registrar"),
 ("M-06","Grades & Transcripts","admin, lecturer"),
 ("M-07","Fees & Payments","admin, finance"),
 ("M-08","Reports & Dashboard","admin, registrar, finance"),
 ("M-09","System Audit Log","admin"),
 ("M-10","AI Decision Support","all staff; student self-scoped"),
])

# ---- Slide 5: ERD
s = prs.slides.add_slide(BLANK); title(s, "Data model", "3NF · 15 tables · 21 foreign keys · 3 computed views")
image(s, os.path.join(DIAG,"F4_erd.png"), top=1.7, height=5.2)

# ---- Slide 6: security
s = prs.slides.add_slide(BLANK); title(s, "Security — defence in depth", "Verified, not assumed")
table(s, ["Layer","Mechanism"], [
 ("Credentials","BCrypt (cost 12), no plaintext"),
 ("Session","HttpOnly, SameSite=Strict, 60-min TTL"),
 ("Access","AuthFilter + per-servlet RBAC; 5 roles / 13 permissions"),
 ("Database","21 FKs, UNIQUE/CHECK constraints, parameterized SQL"),
 ("Anti-IDOR","students self-scoped on every endpoint"),
 ("Audit","append-only; denials recorded"),
 ("AI isolation","loopback-only, token-gated, fail-closed 503"),
])

# ---- Slide 7: AI
s = prs.slides.add_slide(BLANK); title(s, "AI Decision Support (M-10)", "Isolated FastAPI service, real metrics")
m = MLP["metrics"]
table(s, ["Model","Metric","Value"], [
 ("mlp_performance","R²", m["r2"]),
 ("mlp_performance","MAE / RMSE", f"{m['mae']} / {m['rmse']}"),
 ("mlp_performance","Risk precision / recall / F1",
   f"{m['risk_precision']} / {m['risk_recall']} / {m['risk_f1']}"),
 ("lstm_enrollment","val RMSE (scaled)", LSTM["metrics"]["val_rmse_scaled"]),
])
bullets(s, ["Trained on a synthetic generator (disclosed); threshold = 60.",
            "Graceful degradation: AI down → /api/ai/* 503, rest of app unaffected."], top=4.6, size=13)

# ---- Slide 8: verification
s = prs.slides.add_slide(BLANK); title(s, "Verification", "Every number from a run")
table(s, ["Suite","Result"], [
 ("smoke-test.ps1","23 / 23 PASS"),
 ("verify-ai.py","19 / 19 PASS"),
 ("pytest","14 / 14 PASS"),
 ("Total","56 / 56 PASS"),
 ("RBAC per role","verified"),
 ("IDOR probes","no leaks"),
])
bullets(s, ["Verdict: READY for course scope.","Build compiles; single-port AI works end to end."], top=4.6, size=13)

# ---- Slide 9: conclusion
s = prs.slides.add_slide(BLANK); title(s, "Conclusion", None)
bullets(s, [
 "A genuine MIS: collects, processes, transforms, distributes, protects academic information.",
 "Meets the SRS, extended by the AI module (M-10).",
 "56/56 automated checks; RBAC + anti-IDOR verified live.",
 "Single public port; AI isolated, token-gated and fail-closed.",
 "Honest limitations disclosed (synthetic training data; no saved ROC artifacts).",
], top=1.9, size=17)

prs.save(OUT)
print("Saved:", OUT, "| slides:", len(prs.slides.__iter__.__self__._sldIdLst))
