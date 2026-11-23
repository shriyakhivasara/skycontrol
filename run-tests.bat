@echo off
REM Compiles everything and runs every test suite.
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac -d out -cp "lib/*" src\Main.java src\model\*.java src\policy\*.java src\engine\*.java src\events\*.java src\exceptions\*.java src\data\*.java src\report\*.java src\gui\*.java src\experiment\*.java src\test\*.java
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)
set FAILED=0
for %%T in (SchedulerTest SimulationTest ControllerTest DataTest ConcurrencyTest) do (
    java -cp "out;lib/*" test.%%T
    if errorlevel 1 set FAILED=1
)
if %FAILED%==0 (echo ALL TESTS PASSED) else (echo SOME TESTS FAILED)
pause
