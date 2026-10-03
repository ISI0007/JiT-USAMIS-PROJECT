# USAMIS end-to-end smoke test
#
# Exercises the running USAMIS server over HTTP: auth, RBAC, every read API,
# and a full CRUD lifecycle. Exit code 0 = all green; 1 = at least one failure.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-test.ps1
#   powershell ... -File tools\smoke-test.ps1 -Base http://127.0.0.1:8080/usamis
#
# Prereqs: the app is running, the DB is seeded (see README), and the demo
# accounts below exist. Credentials match java/src/main/resources/seed.sql.

param(
  [string]$Base = "http://127.0.0.1:8080/usamis"
)

$ErrorActionPreference = 'SilentlyContinue'
$Api = "$Base/api"
# Unique client IP for this run so the login rate limiter never leaks state
# between runs (or from other probes hitting the same host).
$script:ClientIp = "10.99." + (Get-Random -Minimum 1 -Maximum 254) + "." + (Get-Random -Minimum 1 -Maximum 254)

$script:ok = 0
$script:fail = 0

function Check([string]$name, [bool]$cond, [string]$detail) {
  if ($cond) { $script:ok++; Write-Host ("  [PASS] " + $name + "  " + $detail) -ForegroundColor Green }
  else       { $script:fail++; Write-Host ("  [FAIL] " + $name + "  " + $detail) -ForegroundColor Red }
}

function New-Session {
  return New-Object Microsoft.PowerShell.Commands.WebRequestSession
}

function Login([string]$u, [string]$p) {
  $s = New-Session
  $body = "{`"username`":`"$u`",`"password`":`"$p`"}"
  Invoke-WebRequest "$Api/auth/login" -Method POST -WebSession $s -UseBasicParsing `
    -TimeoutSec 15 -ContentType "application/json" -Headers @{ "X-Forwarded-For" = $script:ClientIp } `
    -Body $body | Out-Null
  return $s
}

function Req($sess, [string]$method, [string]$url, [string]$body) {
  $p = @{ Uri = $url; Method = $method; WebSession = $sess; UseBasicParsing = $true; TimeoutSec = 15 }
  # A unique client IP per run keeps this suite independent of the login rate
  # limiter (which keys on X-Forwarded-For) and of any other probes.
  $p.Headers = @{ "X-Forwarded-For" = $script:ClientIp }
  if ($body) { $p.ContentType = "application/json"; $p.Body = $body }
  try {
    $r = Invoke-WebRequest @p
    return @{ code = [int]$r.StatusCode; body = $r.Content }
  } catch {
    $resp = $_.Exception.Response
    if ($resp) {
      $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
      return @{ code = [int]$resp.StatusCode; body = $sr.ReadToEnd() }
    }
    return @{ code = -1; body = $_.Exception.Message }
  }
}

# ---- 0. availability ----------------------------------------------------
Write-Host ""
Write-Host "=== USAMIS smoke test ===" -ForegroundColor Cyan
Write-Host "Base: $Base"

$r = Req (New-Session) "GET" "$Api/health" $null
$h = $r.body | ConvertFrom-Json
Check "GET /health" ($r.code -eq 200) ("HTTP " + $r.code)
Check "status UP" ($h.status -eq "UP") ("status=" + $h.status)

# ---- 1. authentication --------------------------------------------------
Write-Host ""
Write-Host "[auth]"
$creds = @(
  @("admin001", "admin123", "admin"),
  @("reg001",   "reg123",   "registrar"),
  @("lec001",   "lec123",   "lecturer"),
  @("fin001",   "fin123",   "finance"),
  @("stu001",   "stu123",   "student")
)
# NOTE: do not name this $S — PowerShell is case-insensitive and the loop's
# local session is $s, so $S and $s would be the *same* variable (the map would
# be clobbered by each New-Session call, silently breaking every stored session).
$sessions = @{}
foreach ($c in $creds) {
  $s = New-Session
  $body = "{`"username`":`"$($c[0])`",`"password`":`"$($c[1])`"}"
  $res = $null
  try {
    $res = Invoke-WebRequest "$Api/auth/login" -Method POST -WebSession $s -UseBasicParsing `
      -TimeoutSec 15 -ContentType "application/json" `
      -Headers @{ "X-Forwarded-For" = $script:ClientIp } -Body $body
  } catch {}
  $role = $null
  if ($res) { $role = ($res.Content | ConvertFrom-Json).user.roleName }
  if (-not $role) {
    $st = if ($res) { [int]$res.StatusCode } else { "no-response" }
    Write-Host ("    (login " + $c[0] + " status=" + $st + ")") -ForegroundColor DarkGray
  }
  Check ("login " + $c[0]) ($role -eq $c[2]) ("role=" + $role)
  $sessions[$c[0]] = $s
}

$anon = New-Session
$r = Req $anon "GET" "$Api/students" $null
Check "anonymous blocked" ($r.code -eq 401) ("HTTP " + $r.code)

# ---- 2. read APIs -------------------------------------------------------
Write-Host ""
Write-Host "[read APIs as admin]"
foreach ($ep in @("/dashboard", "/students", "/courses", "/enrollments", "/grades", "/fees", "/users", "/audit?limit=100")) {
  $r = Req $sessions["admin001"] "GET" ($Api + $ep) $null
  Check ("GET " + $ep) ($r.code -eq 200) ("HTTP " + $r.code)
}

# ---- 3. RBAC ------------------------------------------------------------
Write-Host ""
Write-Host "[rbac]"
$r = Req $sessions["stu001"] "GET" "$Api/audit?limit=5" $null
Check "student -> /audit = 403" ($r.code -eq 403) ("HTTP " + $r.code)
$r = Req $sessions["stu001"] "GET" "$Api/users" $null
Check "student -> /users = 403" ($r.code -eq 403) ("HTTP " + $r.code)

# ---- 4. CRUD lifecycle --------------------------------------------------
Write-Host ""
Write-Host "[crud lifecycle]"
$sid = "STU" + (Get-Date -Format "yyyyHHmmss")
$create = '{"studentId":"' + $sid + '","firstName":"Smoke","lastName":"Test","email":"smoke.' + $sid + '@jit.edu.cn","departmentId":1,"programId":1,"yearOfStudy":1}'
$r = Req $sessions["admin001"] "POST" "$Api/students" $create
$newId = ($r.body | ConvertFrom-Json).id
Check "CREATE student" ($r.code -eq 200 -and $newId -gt 0) ("HTTP " + $r.code + " id=" + $newId)

$r = Req $sessions["admin001"] "GET" ("$Api/students/" + $newId) $null
Check "READ back" ($r.code -eq 200) ("HTTP " + $r.code)

$r = Req $sessions["admin001"] "PUT" ("$Api/students/" + $newId) '{"firstName":"SmokeUpdated","yearOfStudy":2}'
Check "UPDATE student" ($r.code -eq 200) ("HTTP " + $r.code)

$r = Req $sessions["admin001"] "POST" "$Api/students" $create
Check "duplicate -> 409" ($r.code -eq 409) ("HTTP " + $r.code)

$r = Req $sessions["admin001"] "DELETE" ("$Api/students/" + $newId) $null
Check "DELETE (soft) student" ($r.code -eq 200) ("HTTP " + $r.code)

# ---- summary ------------------------------------------------------------
Write-Host ""
if ($script:fail -eq 0) {
  Write-Host ("ALL GREEN: " + $script:ok + "/" + ($script:ok + $script:fail) + " checks passed") -ForegroundColor Green
  exit 0
} else {
  Write-Host ($script:fail + " FAILED, " + $script:ok + " passed") -ForegroundColor Red
  exit 1
}
