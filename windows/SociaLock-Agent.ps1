# SociaLock Windows-modul – percenként fut (SYSTEM feladat). Olvassa a szerver állapotát (csak olvasó kulccsal),
# és a tiltási időszakban a tiltott oldalakat a hosts fájlban blokkolja (minden böngészőben), a megfelelő
# asztali alkalmazásokat (Messenger, WhatsApp, Outlook stb.) pedig tűzfalszabállyal elzárja az internettől.
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
  $ep = 'https://sb.trendz.hu/api.php'
  $epFile = Join-Path $dir 'endpoint.txt'
  if (Test-Path $epFile) { $x = (Get-Content $epFile -Raw).Trim(); if ($x -like 'https://sb.trendz.hu/*' -or $x -like 'https://app.socialock.hu/*') { $ep = $x } }
  $st = Invoke-RestMethod -Uri ($ep + '?r=state') -Headers @{ 'X-Key' = (Get-Key) } -TimeoutSec 15
  $st | ConvertTo-Json -Depth 10 | Set-Content -Path $stateFile -Encoding UTF8
} catch {
  # Hiba/offline: az utolsó ismert állapottal dolgozunk tovább (nem lazítunk).
  if (Test-Path $stateFile) { $st = Get-Content $stateFile -Raw | ConvertFrom-Json } else { exit 0 }
}

$blocking = $false; $sites = @(); $apps = @()
if ($st -and $st.rules -and $st.rules.enabled) {
  $nowEpoch = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
  $unlocked = $st.unlocked_until -and ([long]$st.unlocked_until -gt $nowEpoch)
  $sites = @($st.rules.sites | Where-Object { $_ -match '^[a-z0-9.-]+\.[a-z]{2,}$' })
  $apps = @($st.rules.apps)
  $blocking = (-not $unlocked) -and ($sites.Count -gt 0 -or $apps.Count -gt 0) -and (In-Schedule $st.rules.windows (Get-Date))
}

$lines = @(Get-Content $hostsFile -ErrorAction Stop)
$out = New-Object Collections.Generic.List[string]; $skip = $false
foreach ($l in $lines) {
  if ($l.Trim() -eq $B) { $skip = $true; continue }
  if ($l.Trim() -eq $E) { $skip = $false; continue }
  if (-not $skip) { $out.Add($l) }
}
if ($blocking -and $sites.Count -gt 0) {
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

# ---- Böngészőben már nyitott oldalak (pl. Messenger-fül): IP-alapú tűzfalszabály ----
# A hosts-tiltás csak az új kapcsolatokat fogja meg; a már nyitott, élő kapcsolatot (chat) ez a szabály vágja el.
try {
  $SR = 'SociaLock-sites'
  $ips = New-Object Collections.Generic.SortedSet[string]
  if ($blocking) {
    foreach ($d in $sites) {
      foreach ($p in @('', 'www.', 'm.', 'web.', 'edge-chat.', 'gateway.', 'star-mini.c10r.', 'chat-e2ee.')) {
        foreach ($t in @('A', 'AAAA')) {
          foreach ($r in @(Resolve-DnsName -Name "$p$d" -Type $t -Server 1.1.1.1 -DnsOnly -QuickTimeout -ErrorAction SilentlyContinue)) {
            if ($r.IP4Address) { $o = $r.IP4Address.Split('.'); [void]$ips.Add("$($o[0]).$($o[1]).$($o[2]).0/24") }
            elseif ($r.IP6Address) { $g = ([Net.IPAddress]::Parse($r.IP6Address)).GetAddressBytes(); [void]$ips.Add((([Net.IPAddress]::new([byte[]]($g[0..5] + (,0 * 10)))).ToString()) + '/48') }
          }
        }
      }
    }
  }
  $cur = Get-NetFirewallRule -Name $SR -ErrorAction SilentlyContinue
  $sig = ($ips -join ',')
  if ($ips.Count -eq 0) {
    if ($cur) { Remove-NetFirewallRule -Name $SR -ErrorAction SilentlyContinue }
  } elseif (-not $cur -or $cur.Description -ne $sig) {
    if ($cur) { Remove-NetFirewallRule -Name $SR -ErrorAction SilentlyContinue }
    New-NetFirewallRule -Name $SR -DisplayName 'SociaLock – tiltott oldalak' -Description $sig -Group 'SociaLock' -Direction Outbound -Action Block -RemoteAddress @($ips) -Profile Any | Out-Null
  }
} catch { }

# ---- Asztali alkalmazások: tűzfalszabály (csak kimenő forgalom tiltása) a tiltási időszakban ----
# A telefonon tiltott appok (Android csomagnév) és oldalak alapján dől el, melyik asztali programot zárjuk el.
try {
  $G = 'SociaLock'
  $U = 'C:\Users\*\AppData'
  $mail = @{ pkg = @('Microsoft.OutlookForWindows', 'microsoft.windowscommunicationsapps'); exe = @("$env:ProgramFiles\Microsoft Office\root\Office16\OUTLOOK.EXE", "${env:ProgramFiles(x86)}\Microsoft Office\root\Office16\OUTLOOK.EXE", "$env:ProgramFiles\Mozilla Thunderbird\thunderbird.exe", "${env:ProgramFiles(x86)}\Mozilla Thunderbird\thunderbird.exe") }
  $map = @{
    'com.facebook.orca'                   = @{ pkg = @('FACEBOOK.317180B0BB486'); exe = @("$U\Local\Programs\Messenger\Messenger.exe") }
    'com.facebook.katana'                 = @{ pkg = @('Facebook.Facebook'); exe = @() }
    'com.instagram.android'               = @{ pkg = @('Facebook.InstagramBeta'); exe = @() }
    'com.whatsapp'                        = @{ pkg = @('5319275A.WhatsAppDesktop'); exe = @("$U\Local\WhatsApp\WhatsApp.exe") }
    'com.whatsapp.w4b'                    = @{ pkg = @('5319275A.WhatsAppDesktop'); exe = @("$U\Local\WhatsApp\WhatsApp.exe") }
    'org.telegram.messenger'              = @{ pkg = @('TelegramMessengerLLP.TelegramDesktop'); exe = @("$U\Roaming\Telegram Desktop\Telegram.exe") }
    'com.discord'                         = @{ pkg = @(); exe = @("$U\Local\Discord\app-*\Discord.exe") }
    'com.viber.voip'                      = @{ pkg = @(); exe = @("$U\Local\Viber\Viber.exe") }
    'com.zhiliaoapp.musically'            = @{ pkg = @('BytedancePte.Ltd.TikTok'); exe = @() }
    'com.ss.android.ugc.trill'            = @{ pkg = @('BytedancePte.Ltd.TikTok'); exe = @() }
    'com.twitter.android'                 = @{ pkg = @('9E2F88E3.Twitter'); exe = @() }
    'com.google.android.gm'               = $mail
    'com.microsoft.office.outlook'        = $mail
    'com.yahoo.mobile.client.android.mail' = $mail
  }
  $siteMap = @{ 'messenger.com' = 'com.facebook.orca'; 'facebook.com' = 'com.facebook.katana'; 'instagram.com' = 'com.instagram.android'; 'tiktok.com' = 'com.zhiliaoapp.musically'; 'twitter.com' = 'com.twitter.android'; 'x.com' = 'com.twitter.android'; 'mail.google.com' = 'com.google.android.gm'; 'mail.yahoo.com' = 'com.yahoo.mobile.client.android.mail' }

  $want = @{}   # szabálynév -> @{ prog = 'út' } vagy @{ pkgSid = 'S-1-15-2-…'; dir = 'telepítési mappa' }
  if ($blocking) {
    $keys = New-Object Collections.Generic.HashSet[string]
    foreach ($a in $apps) { if ($map.ContainsKey("$a")) { [void]$keys.Add("$a") } }
    foreach ($d in $sites) { if ($siteMap.ContainsKey("$d")) { [void]$keys.Add($siteMap["$d"]) } }
    if ($keys.Count -gt 0) {
      if (-not ('SlkSid' -as [type])) {
        Add-Type -TypeDefinition @"
using System; using System.Runtime.InteropServices;
public static class SlkSid {
  [DllImport("userenv.dll", CharSet = CharSet.Unicode)] static extern int DeriveAppContainerSidFromAppContainerName(string n, out IntPtr sid);
  [DllImport("advapi32.dll", CharSet = CharSet.Unicode)] static extern bool ConvertSidToStringSid(IntPtr sid, out string s);
  [DllImport("advapi32.dll")] static extern IntPtr FreeSid(IntPtr sid);
  public static string Of(string fam) { IntPtr p; if (DeriveAppContainerSidFromAppContainerName(fam, out p) != 0) return null; string s; ConvertSidToStringSid(p, out s); FreeSid(p); return s; }
}
"@
      }
      $allPkgs = @(Get-AppxPackage -AllUsers -ErrorAction SilentlyContinue)
      foreach ($k in $keys) {
        foreach ($pattern in @($map[$k].exe)) {
          foreach ($f in @(Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue)) {
            $h = -join ([Security.Cryptography.MD5]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($f.FullName.ToLower())) | Select-Object -First 6 | ForEach-Object { $_.ToString('x2') })
            $want["SociaLock-exe-" + $h] = @{ prog = $f.FullName }
          }
        }
        foreach ($n in @($map[$k].pkg)) {
          foreach ($p in @($allPkgs | Where-Object { $_.Name -eq $n })) {
            $sid = [SlkSid]::Of($p.PackageFamilyName)
            if ($sid) { $want["SociaLock-pkg-" + $p.PackageFamilyName] = @{ pkgSid = $sid; dir = $p.InstallLocation } }
          }
        }
      }
    }
  }

  $have = @{}
  foreach ($r in @(Get-NetFirewallRule -Group $G -ErrorAction SilentlyContinue)) { $have[$r.Name] = $r }
  # Ami már nem kell (tiltás vége, vagy app lekerült a listáról): törlés.
  foreach ($n in @($have.Keys)) { if ($n -ne 'SociaLock-sites' -and -not $want.ContainsKey($n)) { Remove-NetFirewallRule -Name $n -ErrorAction SilentlyContinue } }
  # Ami hiányzik: létrehozás, és az épp futó példány egyszeri bezárása, hogy a meglévő kapcsolata is megszakadjon.
  foreach ($n in @($want.Keys)) {
    if ($have.ContainsKey($n)) { continue }
    $w = $want[$n]
    if ($w.prog) {
      New-NetFirewallRule -Name $n -DisplayName "SociaLock – tiltás ($([IO.Path]::GetFileName($w.prog)))" -Group $G -Direction Outbound -Action Block -Program $w.prog -Profile Any | Out-Null
      Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path -ieq $w.prog } | Stop-Process -Force -ErrorAction SilentlyContinue
    } else {
      New-NetFirewallRule -Name $n -DisplayName "SociaLock – tiltás ($($n.Substring(14)))" -Group $G -Direction Outbound -Action Block -Package $w.pkgSid -Profile Any | Out-Null
      if ($w.dir) { Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($w.dir, [StringComparison]::OrdinalIgnoreCase) } | Stop-Process -Force -ErrorAction SilentlyContinue }
    }
  }
} catch {
  # A tűzfal-rész hibája nem akadályozza a böngészős (hosts) tiltást.
}
