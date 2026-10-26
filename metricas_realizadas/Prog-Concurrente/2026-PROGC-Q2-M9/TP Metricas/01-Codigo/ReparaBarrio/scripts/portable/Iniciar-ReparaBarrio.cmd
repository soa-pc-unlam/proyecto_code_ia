@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar-portable.ps1"
if errorlevel 1 (
  echo No se pudo iniciar ReparaBarrio. Revise el mensaje anterior.
  pause
  exit /b 1
)
