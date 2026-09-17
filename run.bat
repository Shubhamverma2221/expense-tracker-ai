@echo off
setlocal ENABLEDELAYEDEXPANSION

cd /d "%~dp0"

echo ===================================================
echo     ExpenseAI - Spring Boot Intelligent Engine
echo ===================================================

if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Java\jdk-25.0.2" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-25.0.2"
    ) else (
        where java >nul 2>nul
        if errorlevel 1 (
            echo [ERROR] Java is not installed or not found on PATH.
            echo Please install Java 17+ or JDK 21/25.
            pause
            exit /b 1
        )
    )
)

echo [INFO] JAVA_HOME is: %JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

set MVN_CMD=mvn
where mvn >nul 2>nul
if errorlevel 1 (
    if exist "C:\Users\priya\apache-maven-3.9.9\bin\mvn.cmd" (
        set "MVN_CMD=C:\Users\priya\apache-maven-3.9.9\bin\mvn.cmd"
    ) else (
        echo [ERROR] Maven is not found.
        pause
        exit /b 1
    )
)

if exist "target\expense-tracker-ai-1.0.0.jar" (
    echo [INFO] Launching pre-built JAR on http://127.0.0.1:5000 ...
    java -jar target\expense-tracker-ai-1.0.0.jar
) else (
    echo [INFO] Starting Spring Boot server via Maven on http://127.0.0.1:5000 ...
    call "%MVN_CMD%" spring-boot:run
)

if errorlevel 1 (
    echo.
    echo [ERROR] Application failed to start or exited with an error.
    pause
    exit /b 1
)

endlocal
exit /b 0
