#!/usr/bin/env bash
set -euo pipefail
[ "${CI:-}" = true ]
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
uid=$(id -u)
sudo iptables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT
sudo ip6tables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT
trap 'sudo iptables -D OUTPUT -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT; sudo ip6tables -D OUTPUT -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT' EXIT

# Sanitized ADB diagnostic (text only, CI private artifact via /tmp/*.log). Only phase, state words and times:
# never serials, package data, user text, keys or raw adb output.
ADB_DIAG=/tmp/gabitama-ime-adb-recovery.log
adb_diag() { printf '%s phase=%s event=%s\n' "$(date -u +%H:%M:%S)" "$1" "$2" >> "$ADB_DIAG"; }

# Limited recovery when the emulator drops to "offline" between steps. It waits for reconnection and
# boot completion, then requires both packages to be present. It never clears data, never reinstalls,
# and never converts an unreachable device into a pass: after the budget it fails explicitly.
ensure_device_ready() {
  local phase=$1 attempt state boot
  for attempt in 1 2 3 4 5; do
    state=$(timeout 15 adb get-state 2>/dev/null || true)
    if [ "$state" = device ]; then
      boot=$(timeout 15 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
      if [ "$boot" = 1 ]; then
        [ "$attempt" -gt 1 ] && adb_diag "$phase" "recovered-after-attempt-$attempt"
        return 0
      fi
      adb_diag "$phase" "device-up-boot-pending"
    else
      adb_diag "$phase" "state-${state:-unreachable}"
    fi
    timeout 90 adb wait-for-device || true
  done
  adb_diag "$phase" "unavailable-budget-exhausted"
  echo "::error title=ADB indisponível::fase=$phase; diagnóstico sanitizado no artefato privado de CI" >&2
  return 1
}

sha256sum -c candidate/SHA256SUMS
ensure_device_ready after-boot
adb install -r candidate/app-debug.apk
adb_diag after-install-app ok
ensure_device_ready after-app-install
adb install -r candidate/app-debug-androidTest.apk
adb_diag after-install-test ok
ensure_device_ready after-test-install
for package in com.novoagentehb.app.debug com.novoagentehb.app.debug.test; do
  path=$(adb shell pm path "$package" | tr -d '\r' | sed -n 's/^package://p')
  test -n "$path"
  adb exec-out cat "$path" > "/tmp/$package.apk"
  adb_diag "pm-path-$package" ok
done
cmp candidate/app-debug.apk /tmp/com.novoagentehb.app.debug.apk
cmp candidate/app-debug-androidTest.apk /tmp/com.novoagentehb.app.debug.test.apk
failed=0
for test in chatImeInputDiagnosticsPersistsAcrossRestartAndClears goalsRemindersMemoryAndLibraryPersistRealMutations; do
  ensure_device_ready "before-$test" || { failed=1; break; }
  adb shell am instrument -w -r -e class "com.novoagentehb.app.FunctionalFlowsAndPersistenceAndroidTest#$test" com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > "/tmp/$test.log" 2>&1 || failed=1
  python3 scripts/android/ime-failure-classify.py "$test" "/tmp/$test.log" || failed=1
done
# Only the test harness' fixed boolean/insets log is exposed; no user text.
adb logcat -d -s GabiTestEvidence:I '*:S' | grep -E 'ime_(before|after) visible=' | sed -E 's/.*(ime_(before|after) visible=(true|false|null) windowFocus=(true|false)).*/::notice title=IME state::\1/' || true
exit "$failed"
