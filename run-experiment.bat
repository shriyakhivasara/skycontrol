@echo off
REM Runs the FCFS vs priority policy experiment and saves data\exports\experiment_results.csv
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac -d out -cp "lib/*" @sources.txt
del sources.txt
java -cp "out;lib/*" experiment.PolicyExperiment %*
pause
