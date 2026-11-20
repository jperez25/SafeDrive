#!/bin/sh
# Container entrypoint: runs one Gatling simulation, then leaves the report in /results and waits so load.sh can copy it out.
# SIMULATION is the simulation class name without package; LOAD_PROFILE and LOAD_ENV pick profile and environment file.
set -u
# Gatling reads java.lang internals; the Maven plugin passes this flag, so the container must too.
java --add-opens java.base/java.lang=ALL-UNNAMED -Denv="${LOAD_ENV:-cluster}" -Dload.profile="${LOAD_PROFILE:-smoke}" \
  -cp "/load/classes:/load/lib/*" io.gatling.app.Gatling \
  -s "com.qnt.safedrive.tests.load.${SIMULATION}" -rf /results
echo $? > /results/exit-code
touch /results/.done
sleep 600
