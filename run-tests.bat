@echo off
rem Runs all tests. It asks for your MySQL password and uses a separate classpulse_v2_test database.
cd /d "%~dp0"
call "%~dp0build.bat"
if errorlevel 1 (pause & exit /b 1)
rem The results are also saved in docs\test-results.txt, which the project report reads.
java -cp "out\app;out\test;lib\*" classpulse.server.AllTests config\test-db.properties docs\test-results.txt
pause
