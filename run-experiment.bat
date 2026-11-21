@echo off
REM Runs the FCFS vs priority policy experiment and saves data\exports\experiment_results.csv
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
javac -d out -cp "lib/*" src\Main.java src\model\*.java src\policy\*.java src\engine\*.java src\events\*.java src\exceptions\*.java src\data\*.java src\report\*.java src\gui\*.java src\experiment\*.java src\test\*.java
java -cp "out;lib/*" experiment.PolicyExperiment %*
pause
