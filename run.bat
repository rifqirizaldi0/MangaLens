@echo off
set "JAVA_HOME=D:\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;C:\Users\AXIOO\AppData\Local\Android\Sdk\platform-tools;%PATH%"

echo [1/2] Building and installing to phone...
call gradlew.bat installDebug --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo [2/2] Launching MangaLens...
    adb shell am start -n app.mangalens/.MainActivity
    echo Done!
) else (
    echo Build failed.
)
