@echo off
chcp 65001 > nul
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d "%~dp0"

if "%~1"=="" (
    "%JAVA_HOME%\bin\java.exe" -Xmx40G -Dfile.encoding=UTF-8 -jar build\libs\korean-raptor-1.0.0-SNAPSHOT-all.jar
) else (
    "%JAVA_HOME%\bin\java.exe" -Xmx40G -Dfile.encoding=UTF-8 -jar build\libs\korean-raptor-1.0.0-SNAPSHOT-all.jar %*
)
