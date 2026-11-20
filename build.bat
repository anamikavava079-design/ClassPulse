@echo off
rem Compiles the application and the tests into the out folder.
rem javac follows -sourcepath to find every class that the main classes use.
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out\app out\test
javac --release 21 -Xlint:all,-serial -encoding UTF-8 -sourcepath src -d out\app src\classpulse\server\ServerMain.java src\classpulse\client\ClientMain.java
if errorlevel 1 exit /b 1
javac --release 21 -encoding UTF-8 -cp out\app -sourcepath test -d out\test test\classpulse\server\AllTests.java test\classpulse\client\ScreenshotTour.java
if errorlevel 1 exit /b 1
echo Build succeeded.
