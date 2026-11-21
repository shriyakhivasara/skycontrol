#!/bin/sh
# Runs the FCFS vs priority policy experiment and saves data/exports/experiment_results.csv
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -d out -cp "lib/*" $(find src -name '*.java')
java -cp "out:lib/*" experiment.PolicyExperiment "$@"
