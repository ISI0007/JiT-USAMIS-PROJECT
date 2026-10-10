"""
Capture REAL authenticated USAMIS screenshots.

Key discovery: the SPA does not use hash routes. Navigation is done by JS
functions in index.html: navigateToLive(section) / render*(). So the correct
approach is: open the page, perform an IN-PAGE login (POST /api/auth/login from
the page context so the HttpOnly cookie is set by the browser itself), then call
navigateToLive('<section>') and screenshot.
"""
import base64, json, os, subprocess, time, urllib.request, hashlib

BASE = "http://127.0.0.1:8080/usamis"
OUT  = r"C:\Users\Admin\.openclaw\workspace\JiT-USAMIS-PROJECT\docs\screenshots"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
os.makedirs(OUT, exist_ok=True)

import websocket

port = 9334
proc = subprocess.Popen([CHROME, "--headless=new", "--disable-gpu",
    "--remote-debugging-port=%d" % port, "--remote-allow-origins=*",
    "--window-size=1600,1000", "--hide-scrollbars", "about:blank"],
    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(3)
tabs = json.load(urllib.request.urlopen("http://127.0.0.1:%d/json" % port))
ws_url = [t for t in tabs if t["type"] == "page"][0]["webSocketDebuggerUrl"]
ws = websocket.create_connection(ws_url, timeout=60)
mid = [0]
def send(method, params=None):
    mid[0] += 1
    ws.send(json.dumps({"id": mid[0], "method": method, "params": params or {}}))
    while True:
        m = json.loads(ws.recv())
        if m.get("id") == mid[0]:
            return m.get("result", {})
def js(expr):
    r = send("Runtime.evaluate", {"expression": expr, "awaitPromise": True,
                                  "returnByValue": True})
    return r.get("result", {}).get("value")

send("Page.enable"); send("Runtime.enable")

# 1) open the app
send("Page.navigate", {"url": BASE + "/"})
time.sleep(4)
# make sure the SPA shell + all page-sections are present before driving it
print("shell ready:", js("!!document.getElementById('sec-dashboard') && !!(window.navigateToLive)"))

# 2) login from INSIDE the page so the browser stores the HttpOnly cookie
login_js = """
(async () => {
  const r = await fetch('/usamis/api/auth/login', {
    method:'POST', headers:{'Content-Type':'application/json'},
    body: JSON.stringify({username:'admin001', password:'admin123'})
  });
  return r.status;
})()
"""
print("in-page login:", js(login_js))
time.sleep(2)

# 3) capture the login screen first (fresh, logged out) using a second tab is complex;
#    instead capture current (dashboard) then produce login shot by calling showLogin().
shots = [
    ("02_dashboard",   "navigateToLive('dashboard')"),
    ("03_students",    "navigateToLive('students')"),
    ("04_courses",     "navigateToLive('courses')"),
    ("05_enrollments", "navigateToLive('enrollment')"),
    ("06_grades",      "navigateToLive('grades')"),
    ("07_fees",        "navigateToLive('fees')"),
    ("08_reports",     "navigateToLive('reports')"),
    ("09_audit",       "navigateToLive('audit')"),
    ("10_ai_insights", "navigateToLive('ai')"),
]
def snap(name):
    # Capture using an explicit clip rect of the active section (distinct
    # compositor path from full-viewport capture).
    rect = send("Runtime.evaluate", {"expression":
        "(()=>{const e=document.querySelector('.page-section.active')||document.body;"
        "const r=e.getBoundingClientRect();"
        "return JSON.stringify({x:Math.max(0,r.x),y:Math.max(0,r.y),w:Math.min(1600,r.width),h:Math.min(1000,r.height)});})()",
        "returnByValue": True}).get("result", {}).get("value")
    d = json.loads(rect)
    clip = {"x": d["x"], "y": d["y"], "width": max(400, d["w"]), "height": max(300, d["h"]), "scale": 1}
    s = send("Page.captureScreenshot", {"format": "png", "clip": clip, "captureBeyondViewport": True})
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(base64.b64decode(s["data"]))
    return hashlib.md5(base64.b64decode(s["data"])).hexdigest()[:12]

for name, call in shots:
    js(call)
    time.sleep(3.5)
    # confirm the intended section is active before shooting
    active = js("(()=>{const e=document.querySelector('.page-section.active');return e?e.id:'NONE';})()")
    h = snap(name)
    print(name, "captured (active=" + str(active) + ", hash=" + str(h) + ")")

# login screen last (call showLogin after logout-state)
js("showLogin()")
time.sleep(1.5)
snap("01_login")
print("01_login captured")
ws.close(); proc.terminate()
print("done")
