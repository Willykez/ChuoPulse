#!/usr/bin/env bash
# Needs: kotlinc 2.x, a JDK/JRE, Android stub jar (android.jar), org.json (e.g. json-android.jar), kxml2.jar,
#        python3 with: pip install python-docx openpyxl odfpy
set -e
: "${ANDROID_JAR:?path to android.jar}"; : "${JSON_JAR:?path to json-android.jar}"; : "${KXML_JAR:?path to kxml2.jar}"
HERE="$(cd "$(dirname "$0")" && pwd)"; SRC="$HERE/../../app/src/main/java/com/willykez/campconnect/data"
OUT="$(mktemp -d)"; cd "$OUT"; python3 "$HERE/make_fixtures.py"
CP="$JSON_JAR:$KXML_JAR:$ANDROID_JAR"; RUN="$JSON_JAR:$KXML_JAR:$(dirname "$(command -v kotlinc)")/../lib/kotlin-stdlib.jar"
run() {
  local name="$1" main="$2"; shift 2
  kotlinc -nowarn -cp "$CP" -d "cls_$name" "$@" 2>&1 | grep -v "^warning" || true
  java -cp "cls_$name:$RUN" "$main"
}
run cache SyncCacheTestKt "$HERE/ContextStub.kt" "$HERE/SyncCacheTest.kt" "$SRC/repository/SyncCache.kt"
run office OfficeTestKt "$HERE/OfficeTest.kt" "$SRC/docs/OfficeReader.kt"
run timetable TimetableTestKt "$HERE/TimetableTest.kt" "$SRC/docs/TimetableAssistant.kt" "$SRC/docs/OfficeReader.kt"
