@echo off
rem Starts the server. It asks for your MySQL password once.
cd /d "%~dp0"
if not exist out\app\classpulse\server\ServerMain.class call "%~dp0build.bat"
if errorlevel 1 (pause & exit /b 1)
java -cp "out\app;lib\*" classpulse.server.ServerMain %*
pause
