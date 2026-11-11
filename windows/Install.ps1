# SociaLock Windows-modul telepítő (rendszergazdai jog kell)
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
  Write-Host 'Rendszergazdai jog kell. Futtasd az install.bat-ot.'; Read-Host 'Enter'; exit 1 }
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.Security
$dir = Join-Path $env:ProgramData 'SociaLock'
New-Item -ItemType Directory -Force $dir | Out-Null
Write-Host ''; Write-Host 'SociaLock Windows-modul' -ForegroundColor Cyan
Write-Host 'Add meg az OLVASO (read) kulcsot. A master kulcsot ide NE írd be.'
$sec = Read-Host 'Olvaso kulcs' -AsSecureString
$key = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
if (-not $key) { Write-Host 'Nincs kulcs.'; Read-Host 'Enter'; exit 1 }
try { $null = Invoke-RestMethod -Uri 'https://sb.trendz.hu/api.php?r=state' -Headers @{ 'X-Key' = $key } -TimeoutSec 15 }
catch { Write-Host 'A szerver nem fogadta el a kulcsot vagy nem elérhető: ' $_.Exception.Message -ForegroundColor Red; Read-Host 'Enter'; exit 1 }
$bin = [Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes($key), $null, 'LocalMachine')
[Convert]::ToBase64String($bin) | Set-Content (Join-Path $dir 'key.bin')
Copy-Item (Join-Path $PSScriptRoot 'SociaLock-Agent.ps1') $dir -Force
Copy-Item (Join-Path $PSScriptRoot 'Uninstall.ps1') $dir -Force
& icacls $dir /inheritance:r /grant:r 'SYSTEM:(OI)(CI)F' 'Administrators:(OI)(CI)RX' | Out-Null
$act = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$dir\SociaLock-Agent.ps1`""
$trg = @((New-ScheduledTaskTrigger -AtStartup), (New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 1)))
$prin = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
$set = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 2)
Unregister-ScheduledTask -TaskName 'SociaLock Agent' -Confirm:$false -ErrorAction SilentlyContinue
Register-ScheduledTask -TaskName 'SociaLock Agent' -Action $act -Trigger $trg -Principal $prin -Settings $set | Out-Null
Start-ScheduledTask -TaskName 'SociaLock Agent'
Write-Host 'Kész. A modul percenként frissül, a tiltási időszakban a tiltott oldalak minden böngészőben blokkolva lesznek.' -ForegroundColor Green
Read-Host 'Enter'
