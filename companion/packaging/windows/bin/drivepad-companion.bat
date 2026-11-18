@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "APP_HOME=%%~fI"

set "JAR_PATH="
for %%F in ("%APP_HOME%\lib\drivepad-companion-*-all.jar") do (
    set "JAR_PATH=%%~fF"
    goto :jar_found
)

echo Error: Could not find drivepad-companion-*-all.jar in "%APP_HOME%\lib" 1>&2
exit /b 1

:jar_found
set "JAVA_EXE="
if exist "%APP_HOME%\runtime\bin\java.exe" (
    set "JAVA_EXE=%APP_HOME%\runtime\bin\java.exe"
) else if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA_EXE (
    for /f "delims=" %%J in ('where java 2^>nul') do (
        set "JAVA_EXE=%%J"
        goto :java_found
    )
)

:java_found
if not defined JAVA_EXE (
    echo Error: Java 21+ ^(64-bit^) runtime was not found. 1>&2
    echo Install 64-bit Eclipse Temurin JRE/JDK 21+ from https://adoptium.net/ or place a jlink runtime under "%APP_HOME%\runtime". 1>&2
    exit /b 1
)

"%JAVA_EXE%" -jar "%JAR_PATH%" %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%
