param(
  [string]$Base = "http://127.0.0.1:8101/usamis/api"
)
$ErrorActionPreference = 'SilentlyContinue'
$script:ok = 0
$script:fail = 0

function Check([string]$name, [bool]$cond, [string]$detail) {
  if ($cond) { $script:ok++; Write-Output ("  [PASS] " + $name + "  " + $detail) }
  else       { $script:fail++; Write-Output ("  [FAIL] " + $name + "  " + $detail) }
}

function Sess-Login([string]$u, [string]$p) {
  $s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
  $body = "{`"username`":`"$u`",`"password`":`"$p`"}"
  Invoke-WebRequest "$Base/auth/login" -Method POST -WebSession $s -UseBasicParsing `
    -TimeoutSec 15 -ContentType "application/json" -Body $body | Out-Null
  return $s
}

function Req($sess, [string]$method, [string]$url, [string]$body) {
  $p = @{ Uri = $url; Method = $method; WebSession = $sess; UseBasicParsing = $true; TimeoutSec = 15 }
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

# Build JSON without nested-quote hazards
function J([string]$u, [string]$p) { return '{"username":"' + $u + '","password":"' + $p + '"}' }

$adm = Sess-Login "admin001" "admin123"

Write-Output "=== GRADES full lifecycle (contract: enrollmentId + score) ==="
$code = "CS" + (Get-Random -Minimum 1000 -Maximum 9999)
$cid = ((Req $adm "POST" "$Base/courses" ('{"code":"' + $code + '","name":"GTest","credits":3}')).body | ConvertFrom-Json).id
Check "course created" ($cid -gt 0) ("courseId=" + $cid)

$enrBody = '{"studentId":1,"courseId":' + $cid + ',"semester":"Semester 1"}'
$enrId = ((Req $adm "POST" "$Base/enrollments" $enrBody).body | ConvertFrom-Json).enrollmentId
Check "enrollment created" ($enrId -gt 0) ("enrollmentId=" + $enrId)

$gradeBody = '{"enrollmentId":' + $enrId + ',"score":88.5}'
$r = Req $adm "POST" "$Base/grades" $gradeBody
Check "POST /grades = 200" ($r.code -eq 200) ("HTTP " + $r.code)
Write-Output ("   " + ($r.body -replace "\s+", " "))
$gj = $r.body | ConvertFrom-Json
Check "letterGrade returned" ($null -ne $gj.letterGrade) ("letter=" + $gj.letterGrade + " gpa=" + $gj.gpaPoints)

$gradeId = $gj.gradeId
$r = Req $adm "PUT" "$Base/grades/$gradeId" '{"score":95}'
Check "PUT /grades/$gradeId = 200" ($r.code -eq 200) ("HTTP " + $r.code)

$r = Req $adm "POST" "$Base/grades" $gradeBody
Check "duplicate grade -> 409" ($r.code -eq 409) ("HTTP " + $r.code)

$r = Req $adm "POST" "$Base/grades" ('{"enrollmentId":' + $enrId + ',"score":150}')
Check "score 150 -> 400" ($r.code -eq 400) ("HTTP " + $r.code)

Write-Output ""
Write-Output "=== GRADES RBAC: student cannot enter grades ==="
$stu = Sess-Login "stu001" "stu123"
$r = Req $stu "POST" "$Base/grades" ('{"enrollmentId":' + $enrId + ',"score":50}')
Check "student POST /grades -> 403" ($r.code -eq 403) ("HTTP " + $r.code)

Write-Output ""
Write-Output "=== student scope on GET /grades ==="
$r = Req $stu "GET" "$Base/grades" $null
$scope = ($r.body -replace "\s+", " ")
if ($scope.Length -gt 200) { $scope = $scope.Substring(0, 200) }
Write-Output ("   HTTP " + $r.code + "  " + $scope)

Write-Output ""
Write-Output "=== cleanup ==="
$dc = (Req $adm "DELETE" "$Base/courses/$cid" $null).code
$de = (Req $adm "DELETE" "$Base/enrollments/$enrId" $null).code
Write-Output ("   delete course -> " + $dc + " | delete enrollment -> " + $de)

Write-Output ""
Write-Output (" SUBTOTAL: " + $script:ok + " passed, " + $script:fail + " failed")
