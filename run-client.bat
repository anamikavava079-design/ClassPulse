@echo off
rem Opens one client window. Run this once for the teacher and once per student.
cd /d "%~dp0"
if not exist out\app\classpulse\client\ClientMain.class call "%~dp0build.bat"
if errorlevel 1 (pause & exit /b 1)
start "" javaw -cp "out\app" classpulse.client.ClientMain
