"""Diagnose: after in-page login + navigateToLive, is the DOM actually switching?"""
import json, subprocess, time, urllib.request, base64, os
import websocket
BASE="http://127.0.0.1:8080/usamis"
CHROME=r"C:\Program Files\Google\Chrome\Application\chrome.exe"
port=9335
proc=subprocess.Popen([CHROME,"--headless=new","--disable-gpu","--remote-debugging-port=%d"%port,
 "--remote-allow-origins=*","--window-size=1600,1000","--hide-scrollbars","about:blank"],
 stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
time.sleep(3)
tabs=json.load(urllib.request.urlopen("http://127.0.0.1:%d/json"%port))
ws=websocket.create_connection([t for t in tabs if t["type"]=="page"][0]["webSocketDebuggerUrl"],timeout=60)
mid=[0]
def send(m,p=None):
    mid[0]+=1; ws.send(json.dumps({"id":mid[0],"method":m,"params":p or {}}))
    while True:
        x=json.loads(ws.recv())
        if x.get("id")==mid[0]: return x.get("result",{})
def js(e):
    r=send("Runtime.evaluate",{"expression":e,"awaitPromise":True,"returnByValue":True})
    if "exceptionDetails" in r: return "ERR:"+str(r["exceptionDetails"].get("text"))
    return r.get("result",{}).get("value")
send("Page.enable"); send("Runtime.enable")
send("Page.navigate",{"url":BASE+"/"}); time.sleep(4)
print("login:", js("""(async()=>{const r=await fetch('/usamis/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin001',password:'admin123'})});return r.status;})()"""))
time.sleep(2)
print("currentUser:", js("typeof currentUser!=='undefined' ? (currentUser&&currentUser.role) : 'undef'"))
for sec in ["dashboard","students","courses","enrollment","grades","fees","reports","audit","ai"]:
    js(f"navigateToLive('{sec}')"); time.sleep(1.2)
    active = js("(()=>{const e=document.querySelector('.page-section.active');return e?e.id:'NONE';})()")
    title  = js("document.getElementById('pageTitle')?document.getElementById('pageTitle').innerText.trim():'?'")
    rows   = js("(()=>{const e=document.querySelector('.page-section.active');return e?e.innerText.replace(/\\s+/g,' ').slice(0,120):'';})()")
    print(f"{sec:11} active={active:20} title={title[:28]:28} | {rows[:70]}")
ws.close(); proc.terminate()
