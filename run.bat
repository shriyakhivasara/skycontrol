@echo off
REM Compiles SkyControl and opens the main window.
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac -d out -cp "lib/*" @sources.txt
if errorlevel 1 (
    del sources.txt
    echo Compilation failed.
    pause
    exit /b 1
)
del sources.txt
java -cp "out;lib/*" Main %*
