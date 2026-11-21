@echo off
REM Compiles everything and runs every test suite.
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
set FAILED=0
for %%T in (SchedulerTest SimulationTest DataTest ConcurrencyTest) do (
    java -cp "out;lib/*" test.%%T
    if errorlevel 1 set FAILED=1
)
if %FAILED%==0 (echo ALL TESTS PASSED) else (echo SOME TESTS FAILED)
pause
