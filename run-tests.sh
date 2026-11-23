#!/bin/sh
# Compiles everything and runs every test suite.
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -d out -cp "lib/*" $(find src -name '*.java') || exit 1
failed=0
for t in SchedulerTest SimulationTest ControllerTest DataTest ConcurrencyTest; do
    java -cp "out:lib/*" test.$t || failed=1
done
if [ $failed -eq 0 ]; then echo "ALL TESTS PASSED"; else echo "SOME TESTS FAILED"; fi
exit $failed
