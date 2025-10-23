@echo off
setlocal

REM Set JAVA_HOME to JDK 21
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:loop
echo Building and starting Order Processor Service...
mvn spring-boot:run
echo Service stopped. Press any key to restart or Ctrl+C to exit.
pause > nul
goto loop
