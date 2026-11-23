#!/usr/bin/env bash
set -euo pipefail
[ "${CI:-}" = true ]
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
uid=$(id -u)
sudo iptables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT
sudo ip6tables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT
trap 'sudo iptables -D OUTPUT -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT; sudo ip6tables -D OUTPUT -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT' EXIT
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.novoagentehb.app.OriginalShellRuntimeAndroidTest com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > /tmp/tama-shell-instrument.txt 2>&1
python3 - <<'PY'
import re
from pathlib import Path
s=Path('/tmp/tama-shell-instrument.txt').read_text(errors='replace')
if re.search(r'OK \(1 test\)',s) and 'FAILURES!!!' not in s:
 print('::notice title=Original shell runtime::PASS: PRoot boot, persistent shell, exit status and PTY; AVD x86_64')
else:
 print('::error title=Original shell runtime::FAIL')
 for line in s.splitlines():
  if re.match(r'^\s*(?:java\.|kotlinx\.coroutines\.|junit\.framework\.|at com\.(?:novoagentehb|openminis)\.)',line):
   print('::error title=Shell diagnostic::'+line.strip()[:400].replace('%','%25'))
 raise SystemExit(1)
PY
