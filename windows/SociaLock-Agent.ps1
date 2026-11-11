# SociaLock Windows-modul – percenként fut (SYSTEM feladat). Olvassa a szerver állapotát (csak olvasó kulccsal),
# és a tiltási időszakban a tiltott oldalakat a hosts fájlban blokkolja (minden böngészőben).
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.Security
$dir = Join-Path $env:ProgramData 'SociaLock'
$stateFile = Join-Path $dir 'state.json'
$hostsFile = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
$B = '# SOCIALOCK BEGIN'; $E = '# SOCIALOCK END'

function Get-Key {
  $bin = [Convert]::FromBase64String((Get-Content (Join-Path $dir 'key.bin') -Raw).Trim())
  [Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($bin, $null, 'LocalMachine'))
}
function HM($s) { if ("$s" -match '^(\d\d):(\d\d)$') { [int]$Matches[1] * 60 + [int]$Matches[2] } else { $null } }
function In-Schedule($windows, [datetime]$d) {
  $dow = [int]$d.DayOfWeek + 1; $prev = if ($dow -eq 1) { 7 } else { $dow - 1 }; $now = $d.Hour * 60 + $d.Minute
  foreach ($w in @($windows)) {
    $s = HM $w.start; $e = HM $w.end; $days = @($w.days)
    if ($null -eq $s -or $null -eq $e) { continue }
    if ($s -le $e) { if ($days -contains $dow -and $now -ge $s -and $now -lt $e) { return $true } }
    else {
      if ($days -contains $dow -and $now -ge $s) { return $true }
      if ($days -contains $prev -and $now -lt $e) { return $true }
    }
  }
  return $false
}

$st = $null
try {
  $st = Invoke-RestMethod -Uri 'https://sb.trendz.hu/api.php?r=state' -Headers @{ 'X-Key' = (Get-Key) } -TimeoutSec 15
  $st | ConvertTo-Json -Depth 10 | Set-Content -Path $stateFile -Encoding UTF8
} catch {
  # Hiba/offline: az utolsó ismert állapottal dolgozunk tovább (nem lazítunk).
  if (Test-Path $stateFile) { $st = Get-Content $stateFile -Raw | ConvertFrom-Json } else { exit 0 }
}

$blocking = $false; $sites = @()
if ($st -and $st.rules -and $st.rules.enabled) {
  $nowEpoch = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $unlocked = $st.unlocked_until -and ([long]$st.unlocked_until -gt $nowEpoch)
  $sites = @($st.rules.sites | Where-Object { $_ -match '^[a-z0-9.-]+\.[a-z]{2,}$' })
  $blocking = (-not $unlocked) -and $sites.Count -gt 0 -and (In-Schedule $st.rules.windows (Get-Date))
}

$lines = @(Get-Content $hostsFile -ErrorAction Stop)
$out = New-Object Collections.Generic.List[string]; $skip = $false
foreach ($l in $lines) {
  if ($l.Trim() -eq $B) { $skip = $true; continue }
  if ($l.Trim() -eq $E) { $skip = $false; continue }
  if (-not $skip) { $out.Add($l) }
}
if ($blocking) {
  $out.Add($B)
  foreach ($d in $sites) { foreach ($p in @('', 'www.', 'm.', 'mobile.', 'web.')) { $h = "$p$d"; $out.Add("0.0.0.0 $h"); $out.Add(":: $h") } }
  $out.Add($E)
}
$new = ($out -join "`r`n").TrimEnd() + "`r`n"
$old = (Get-Content $hostsFile -Raw)
if ($new.TrimEnd() -ne $old.TrimEnd()) {
  [IO.File]::WriteAllText($hostsFile, $new, (New-Object Text.ASCIIEncoding))
  & ipconfig /flushdns | Out-Null
}
