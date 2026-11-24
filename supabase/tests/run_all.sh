#!/usr/bin/env bash
# Builds a throw-away Postgres database with a Supabase-compatible harness, applies EVERY migration
# in supabase/migrations (twice, to prove they are re-runnable), loads two campuses of test data and
# runs all checks.
#
#   cd supabase/tests && PSQL="psql -U postgres" ./run_all.sh          (needs a local Postgres 15+)
#   Edge Functions (needs Deno):  cd supabase/tests/edge && deno run --allow-all --import-map=import_map.json run.ts
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"; SQL="$HERE/sql"; MIG="$HERE/../migrations"
PSQL="${PSQL:-psql}"; DB="${DB:-cc_test}"
$PSQL -q -c "drop database if exists $DB" -c "create database $DB" || exit 1
P="$PSQL -d $DB -v ON_ERROR_STOP=1 -q"
$P -f "$SQL/00_harness.sql" || exit 1
for f in "$MIG"/*.sql; do $P -f "$f" || { echo "MIGRATION FAILED: $f"; exit 1; }; done
echo "all $(ls "$MIG" | wc -l) migrations applied"
for f in "$MIG"/*.sql; do $P -f "$f" || { echo "RE-RUN FAILED (not idempotent): $f"; exit 1; }; done
echo "all migrations applied a second time (idempotent)"
$P -f "$SQL/fixture.sql" >/dev/null && $P -f "$SQL/test_helpers.sql" || exit 1
rc=0
for t in regress_core regress_new misc_test apply_timetable; do
  out=$($PSQL -d $DB -f "$SQL/$t.sql" 2>&1 | grep -E 'passed|^   |ERROR')
  echo "$t:$out"; echo "$out" | grep -q ' 0 failed' || rc=1
done
echo "--- campus wipe with data in every table:"; $PSQL -d $DB -f "$SQL/wipe_test.sql" 2>&1 | grep -E 'WIPE|after:|queued'
exit $rc
