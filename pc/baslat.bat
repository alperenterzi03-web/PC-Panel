@echo off
REM PC Panel arka plan başlatıcısı (konsol penceresi göstermez)
cd /d "%~dp0"
start "" pythonw "%~dp0panel.py"
