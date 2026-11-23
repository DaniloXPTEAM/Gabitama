#!/usr/bin/env bash
# Experimental two-package proof of concept (option 1). Runs on an emulator in CI with disposable builds.
# Fixed operations only; synthetic data only. Passing this does NOT prove that PRoot, Alpine or a terminal work.
set -u
trap 'status=$?; echo "::error title=Helper PoC::Failure at line $LINENO (status $status)"' ERR
HELPER_APK=android/computer-helper/build/outputs/apk/debug/computer-helper-debug.apk
APP_APK=android/app/build/outputs/apk/debug/app-debug.apk
TEST_APK=android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for f in "$HELPER_APK" "$APP_APK" "$TEST_APK"; do test -f "$f" || { echo "::error title=Helper PoC::missing $f"; exit 1; }; done
adb install -r "$HELPER_APK" || { echo '::error title=Helper PoC setup::helper install failed'; exit 1; }
adb install -r "$APP_APK" || { echo '::error title=Helper PoC setup::app install failed'; exit 1; }
adb install -r "$TEST_APK" || { echo '::error title=Helper PoC setup::test install failed'; exit 1; }
adb shell pm clear com.novoagentehb.computerhelper >/dev/null || true
adb logcat -c || echo '::warning title=Helper PoC::Could not clear log buffer'
set +e
adb shell am instrument -w -r -e class com.novoagentehb.app.computer.helper.ComputerHelperPocAndroidTest \
  com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > /tmp/helper-poc.txt 2>&1
INSTRUMENT_STATUS=$?
set -e
adb logcat -d -s TamaIsolationFixture:V > /tmp/helper-poc-log.txt || true
python3 - <<'PY'
import os, re
from pathlib import Path
out = Path('/tmp/helper-poc.txt').read_text(errors='replace')
for line in Path('/tmp/helper-poc-log.txt').read_text(errors='replace').splitlines():
    if 'pocHelper:' in line:
        print('::notice title=Helper PoC::' + line.split('TamaIsolationFixture:', 1)[1].strip()[:400].replace('%', '%25'))
failed = 'FAILURES!!!' in out or re.search(r'\bFAILURES\b', out) is not None
if os.environ.get('INSTRUMENT_STATUS', '0') != '0' or not re.search(r'OK \(10 tests\)', out) or failed:
    print('::error title=Helper PoC::FAIL; instrumentation output follows')
    for line in out.splitlines():
        if re.search(r'Error|Exception|FAIL|expected|Caused by|at com\.novoagentehb', line):
            print('::error title=Helper PoC diagnostic::' + line.strip()[:400].replace('%', '%25'))
    raise SystemExit(1)
print('::notice title=Helper PoC::OK (10 tests) on API ' + os.environ.get('API_LEVEL', '?'))
PY
