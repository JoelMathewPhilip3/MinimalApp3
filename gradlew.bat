@echo off
setlocal
set GRADLE_VERSION=8.13
set APP_HOME=%~dp0
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_DIR=%GRADLE_USER_HOME%\wrapper\dists\gradle-%GRADLE_VERSION%-bin\manual
set GRADLE_BIN=%DIST_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat
set ZIP_FILE=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip

if exist "%GRADLE_BIN%" goto run
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
if not exist "%ZIP_FILE%" (
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
  if errorlevel 1 exit /b 1
)
echo Extracting Gradle...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP_FILE%' '%DIST_DIR%'"
if errorlevel 1 exit /b 1

:run
call "%GRADLE_BIN%" -p "%APP_HOME%" %*
endlocal
