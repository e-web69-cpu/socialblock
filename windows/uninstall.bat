@echo off
chcp 65001 >nul
powershell -NoProfile -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"%ProgramData%\SociaLock\Uninstall.ps1\"'"
