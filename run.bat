@echo off
REM Compiles SkyControl and opens the main window.
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac -d out -cp "lib/*" src\Main.java src\model\*.java src\policy\*.java src\engine\*.java src\events\*.java src\exceptions\*.java src\data\*.java src\report\*.java src\gui\*.java src\experiment\*.java src\test\*.java
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)
java -cp "out;lib/*" Main %*
