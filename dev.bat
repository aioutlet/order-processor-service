@echo off
setlocal

REM Set JAVA_HOME to JDK 21
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:loop
echo.
echo ============================================
echo Starting order processor service...
echo ============================================
echo.

REM Check if port 8080 is in use and kill the process
echo Checking port 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8080 ^| findstr LISTENING') do (
    echo Port 8080 is in use by PID %%a, killing process...
    taskkill /PID %%a /F >nul 2>&1
    timeout /t 1 >nul
)

echo Starting service on port 8080...
mvn spring-boot:run

echo.
echo ============================================
echo Service stopped. Press any key to restart or Ctrl+C to exit.
echo ============================================
pause > nul
goto loop

