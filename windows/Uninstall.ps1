# SociaLock Windows-modul eltávolítása – szándékosan körülményes.
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
  Write-Host 'Rendszergazdai jog kell.'; Read-Host 'Enter'; exit 1 }
$dir = Join-Path $env:ProgramData 'SociaLock'
$hostsFile = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
if ((Get-Content $hostsFile -Raw) -match '# SOCIALOCK BEGIN') {
  Write-Host 'Tiltási időszak van, a modul most nem távolítható el. Várd ki a tiltás végét.' -ForegroundColor Yellow; Read-Host 'Enter'; exit 1 }
Write-Host 'Biztosan le akarod kapcsolni az önkorlátozásodat?' -ForegroundColor Yellow
$phrase = 'Tudatosan kikapcsolom az onkorlatozasomat a gepemen'
Write-Host "Írd be pontosan (kis- és nagybetűre érzékeny, ékezetek nélkül):`n$phrase"
if ((Read-Host '>') -cne $phrase) { Write-Host 'Nem egyezik. A modul marad.'; Read-Host 'Enter'; exit 1 }
for ($i = 10; $i -gt 0; $i--) { Write-Host "Eltávolítás $i perc múlva... (Ctrl+C: mégsem)"; Start-Sleep -Seconds 60 }
Unregister-ScheduledTask -TaskName 'SociaLock Agent' -Confirm:$false -ErrorAction SilentlyContinue
$lines = @(Get-Content $hostsFile); $out = @(); $skip = $false
foreach ($l in $lines) { if ($l.Trim() -eq '# SOCIALOCK BEGIN') { $skip = $true; continue }; if ($l.Trim() -eq '# SOCIALOCK END') { $skip = $false; continue }; if (-not $skip) { $out += $l } }
[IO.File]::WriteAllText($hostsFile, (($out -join "`r`n").TrimEnd() + "`r`n"), (New-Object Text.ASCIIEncoding))
& ipconfig /flushdns | Out-Null
Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
Write-Host 'Eltávolítva.' -ForegroundColor Green; Read-Host 'Enter'
