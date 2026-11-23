#!/usr/bin/env bash
# Internal disposable ARM64 AVD only; never installs on a physical device.
set -euo pipefail
[ "${CI:-}" = true ]
serial="$(adb get-serialno)"
[[ "$serial" =~ ^emulator-[0-9]+$ ]]
abi="$(adb -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
[ "$abi" = arm64-v8a ]
uid=$(id -u)
sudo iptables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT
sudo ip6tables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT
trap 'sudo iptables -D OUTPUT -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT; sudo ip6tables -D OUTPUT -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT' EXIT
adb -s "$serial" install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$serial" shell am instrument -w -r -e class com.novoagentehb.app.Arm64OriginalShellAndroidTest com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > /tmp/tama-arm64-instrument.txt 2>&1
python3 - <<'PY'
import re
from pathlib import Path
s = Path('/tmp/tama-arm64-instrument.txt').read_text(errors='replace')
if re.search(r'OK \(1 test\)', s) and 'FAILURES!!!' not in s:
    print('::notice title=ARM64 original shell fixture::PASS (fixed commands only; no physical-device validation)')
else:
    print('::error title=ARM64 original shell fixture::FAIL')
    for line in s.splitlines():
        if re.match(r'^\s*(?:java\.|kotlinx\.coroutines\.|junit\.|at com\.(?:novoagentehb|openminis)\.)', line):
            print('::error title=ARM64 fixture diagnostic::' + line.strip()[:400].replace('%','%25'))
    raise SystemExit(1)
PY
