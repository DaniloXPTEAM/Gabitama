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
failed=0
for test in chatImeInputDiagnosticsPersistsAcrossRestartAndClears goalsRemindersMemoryAndLibraryPersistRealMutations; do
  adb shell am instrument -w -r -e class "com.novoagentehb.app.FunctionalFlowsAndPersistenceAndroidTest#$test" com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > "/tmp/$test.log" 2>&1 || failed=1
  python3 scripts/android/ime-failure-classify.py "$test" "/tmp/$test.log" || failed=1
done
# Only the test harness' fixed boolean/insets log is exposed; no user text.
adb logcat -d -s GabiTestEvidence:I '*:S' | grep -E 'ime_(before|after) visible=' | sed -E 's/.*(ime_(before|after) visible=(true|false|null) windowFocus=(true|false)).*/::notice title=IME state::\1/' || true
exit "$failed"
