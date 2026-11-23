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
adb shell am instrument -w -r -e class com.novoagentehb.app.RealAlpineRootfsAndroidTest com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > /tmp/alpine-instrument.txt 2>&1
python3 - <<'PY'
import re
from pathlib import Path
s = Path('/tmp/alpine-instrument.txt').read_text(errors='replace')
if re.search(r'OK \(1 test\)', s) and 'FAILURES!!!' not in s:
    print('::notice title=Real Alpine extraction::PASS (API34 x86_64; archive aarch64, no execution)')
else:
    print('::error title=Real Alpine extraction::FAIL')
    for line in s.splitlines():
        if re.match(r'^\s*(?:java\.lang\.\w+Exception|java\.util\.zip\.\w+Exception|at com\.openminis\.app\.sandbox\.RootfsManager\.|at com\.novoagentehb\.app\.RealAlpineRootfsAndroidTest\.)', line):
            print('::error title=Alpine diagnostic::' + line.strip()[:400].replace('%', '%25'))
    raise SystemExit(1)
PY
