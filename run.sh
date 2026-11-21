#!/bin/sh
# Compiles SkyControl and opens the main window.
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -d out -cp "lib/*" $(find src -name '*.java')
java -cp "out:lib/*" Main "$@"
