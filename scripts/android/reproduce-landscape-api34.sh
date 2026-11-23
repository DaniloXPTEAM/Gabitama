#!/usr/bin/env bash
set -euo pipefail
[ "${CI:-}" = true ]
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
uid=$(id -u)
sudo iptables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT
sudo ip6tables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT
trap 'sudo iptables -D OUTPUT -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT; sudo ip6tables -D OUTPUT -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT' EXIT
sha256sum -c candidate/SHA256SUMS
adb install -r candidate/app-debug.apk
adb install -r candidate/app-debug-androidTest.apk
for package in com.novoagentehb.app.debug com.novoagentehb.app.debug.test; do
    path=$(adb shell pm path "$package" | tr -d '\r' | sed -n 's/^package://p')
    test -n "$path"
    adb exec-out cat "$path" > "/tmp/$package.apk"
done
cmp candidate/app-debug.apk /tmp/com.novoagentehb.app.debug.apk
cmp candidate/app-debug-androidTest.apk /tmp/com.novoagentehb.app.debug.test.apk
adb shell am instrument -w -r -e class 'com.novoagentehb.app.StartupAndLifecycleAndroidTest#landscapeConfigurationKeepsHomeNavigableAndInputAccessible' com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > /tmp/landscape-isolated.txt 2>&1
python3 - <<'PY'
from pathlib import Path
import re
s = Path('/tmp/landscape-isolated.txt').read_text(errors='replace')
ok = bool(re.search(r'OK \(1 test\)', s)) and 'FAILURES!!!' not in s
print(('::notice' if ok else '::error') + ' title=Isolated landscape API34::' + ('PASS' if ok else 'FAIL'))
if not ok:
    for line in s.splitlines():
        if re.match(r'^\s*(?:java\.lang\.\w+Exception|androidx\.\S+Exception|at com\.novoagentehb\.app\.StartupAndLifecycleAndroidTest\.)', line):
            print('::error title=Landscape diagnostic::' + line.strip()[:300].replace('%', '%25'))
    raise SystemExit(1)
PY
