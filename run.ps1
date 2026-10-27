# run.ps1 — jalankan ini kalau mau install ke HP
$env:JAVA_HOME = "D:\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\AXIOO\AppData\Local\Android\Sdk\platform-tools;$env:PATH"
.\gradlew.bat installDebug --no-daemon
adb shell am start -n app.mangalens/.MainActivity
