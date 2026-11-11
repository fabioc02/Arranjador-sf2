@echo off
rem Instala o WX Stage-IV: clique duas vezes (pede permissao de administrador).
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process powershell -Verb RunAs -Wait -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-NoExit','-File','\"%~dp0Instalar.ps1\"','\"%APPDATA%\"'"
