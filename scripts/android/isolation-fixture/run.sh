#!/usr/bin/env bash
set -euo pipefail
trap 'status=$?; echo "::error title=Isolation harness::Failure at line $LINENO (status $status)"' ERR
[ "${CI:-}" = true ]
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
uid=$(id -u)
sudo iptables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT
sudo ip6tables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT
trap 'sudo iptables -D OUTPUT -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT; sudo ip6tables -D OUTPUT -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT' EXIT
adb install -r android/app/build/outputs/apk/debug/app-debug.apk || { echo '::error title=Isolation setup::Target APK installation failed'; exit 1; }
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || { echo '::error title=Isolation setup::Test APK installation failed'; exit 1; }
# API 26 can deny clearing log buffers; the AVD is fresh and output is filtered.
adb logcat -c || echo '::warning title=Isolation fixture::Could not clear emulator log buffer; using filtered logs.'
if adb shell am instrument -w -r -e class com.novoagentehb.app.IsolatedProbeAndroidTest,com.novoagentehb.app.RootfsGrantAndroidTest,com.novoagentehb.app.IsolatedRuntimeProtocolAndroidTest com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > /tmp/tama-isolation.txt 2>&1; then
  export ISOLATION_INSTRUMENT_STATUS=0
else
  export ISOLATION_INSTRUMENT_STATUS=$?
fi
adb logcat -d -s TamaIsolationFixture:V > /tmp/tama-isolation-log.txt || true
adb logcat -d -b crash > /tmp/tama-isolation-crash.txt || true
python3 - <<'PY'
from pathlib import Path
import os
import re
s=Path('/tmp/tama-isolation.txt').read_text(errors='replace')
for line in Path('/tmp/tama-isolation-log.txt').read_text(errors='replace').splitlines():
 if 'TamaIsolationFixture:' in line:
  print('::notice title=Isolation fixture::'+line.split('TamaIsolationFixture:',1)[1].strip())
if os.environ['ISOLATION_INSTRUMENT_STATUS'] != '0' or not re.search(r'OK \(4 tests\)',s) or 'FAILURES!!!' in s:
 print('::error title=Isolation fixture::FAIL; instrumentation exit=' + os.environ['ISOLATION_INSTRUMENT_STATUS'])
 for line in Path('/tmp/tama-isolation-crash.txt').read_text(errors='replace').splitlines():
  if re.search(r'FATAL EXCEPTION|Process:|Exception:|Error:|at com\.novoagentehb|Caused by:', line):
   line = re.sub(r'/data/[^ :]*', '<app-path>', line)
   print('::error title=Isolated process crash::'+line[:400].replace('%','%25'))
 for line in s.splitlines():
  if re.match(r'^\s*(?:java\.|android\.|junit\.|kotlin\.|Caused by|at com\.novoagentehb\.app\.)',line):
   print('::error title=Isolation diagnostic::'+line.strip()[:400].replace('%','%25'))
 raise SystemExit(1)
PY
