@echo off
rem Takes the report's screenshots by clicking through the real app (windows stay hidden).
rem It asks for your MySQL password and uses its own classpulse_v2_screenshots database.
rem Stop run-server.bat first, so the phone-play port 8080 is free for the QR code.
cd /d "%~dp0"
call "%~dp0build.bat"
if errorlevel 1 (pause & exit /b 1)
java -cp "out\app;out\test;lib\*" classpulse.client.ScreenshotTour config\screenshots-db.properties docs\screenshots-v2
pause
