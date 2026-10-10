"""Minimal test: does captureScreenshot ever change in this headless Chrome?"""
import json, subprocess, time, urllib.request, base64, hashlib
import websocket
CHROME=r"C:\Program Files\Google\Chrome\Application\chrome.exe"
port=9336
proc=subprocess.Popen([CHROME,"--headless=new","--disable-gpu","--remote-debugging-port=%d"%port,
 "--remote-allow-origins=*","--window-size=1200,800","about:blank"],
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
    r=send("Runtime.evaluate",{"expression":e,"returnByValue":True,"awaitPromise":True})
    return r.get("result",{}).get("value")
def shot():
    s=send("Page.captureScreenshot",{"format":"png"})
    return hashlib.md5(base64.b64decode(s["data"])).hexdigest()[:12], len(s["data"])
send("Page.enable"); send("Runtime.enable")
send("Page.navigate",{"url":"data:text/html,<html><body style='background:red;font-size:60px'>RED</body></html>"})
time.sleep(2); print("red:", shot())
send("Page.navigate",{"url":"data:text/html,<html><body style='background:blue;font-size:60px'>BLUE</body></html>"})
time.sleep(2); print("blue:", shot())
# now via JS DOM change on same doc
js("document.body.style.background='green';document.body.innerText='GREEN'")
time.sleep(1); print("green(js):", shot())
ws.close(); proc.terminate()
