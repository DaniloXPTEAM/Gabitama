#!/usr/bin/env bash
set -euo pipefail
pkg=com.novoagentehb.app.debug
activity=com.novoagentehb.app.app.MainActivity
stage=preparacao
mkdir -p evidence-android
# GitHub annotations contain no log messages, intents, device identifiers, or personal data.
# Preserve only exception type, process and stack frame names for an ephemeral test AVD.
on_error() {
  local code=$?
  trap - ERR
  echo "::error title=Android smoke::Stage ${stage} failed (exit ${code}); inspect private logcat on a test device."
  adb logcat -d -b crash -v brief > /tmp/gabitama-crash-private.txt 2>/dev/null || :
  python3 - /tmp/gabitama-crash-private.txt <<'PY'
import re, sys
try:
    text = open(sys.argv[1], encoding='utf-8', errors='replace').read()
except OSError:
    text = ''
if 'com.novoagentehb.app.debug' in text:
    # Single annotation avoids GitHub's per-step limit on error annotations.
    lines = []
    for line in text.splitlines():
        match = re.search(r'(?:Caused by: )?([\w.$]+(?:Exception|Error))(?::|\s|$)', line)
        frame = re.search(r'\bat ([\w.$]+\((?:[\w.$]+(?::\d+)?|Unknown Source|Native Method)\))', line)
        if 'NullPointerException' in line:
            for symbol in re.findall(r'com\.novoagentehb\.app\.app\.navigation\.[A-Za-z0-9_.$]+(?:\([^)]{0,100}\))?', line):
                lines.append('Referenced navigation symbol: ' + symbol)
        if match:
            lines.append(('Caused by: ' if 'Caused by:' in line else 'Exception: ') + match.group(1))
        elif frame:
            lines.append('at ' + frame.group(1))
    if lines:
        print('::error title=Sanitized crash stack::' + '%0A'.join(lines[:200]))

PY
  exit "$code"
}
trap on_error ERR
adb devices -l
# Destructive reset is permitted only on the disposable CI emulator.
[ "${CI:-}" = true ] && [[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
[ "$(adb get-state)" = device ]
[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]
adb shell pm path android | grep -q '^package:'
adb shell service check activity | grep -q 'found'
adb shell getprop ro.build.version.sdk
stage=install
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
echo '::notice title=INSTALL_PASS::APK instalado em AVD descartável'
stage=clear
adb shell pm clear "$pkg"
if ! adb logcat -c >/tmp/gabitama-logcat-clear-private.txt 2>&1; then
  echo '::warning title=Android logcat::Buffer não pôde ser limpo; usando delimitação temporal.'
fi
# Device clock, not runner clock: logcat -T reads entries after this time even if clear failed.
log_start=$(adb shell date '+%m-%d %H:%M:%S.000' | tr -d '\r')
stage=start
adb shell am start -W -n "$pkg/$activity" | tee evidence-android/start.txt
if grep -Eq '(^Error:|^Exception|Status: (error|timeout))' evidence-android/start.txt; then exit 1; fi
stage=stability
sleep 8
adb shell pidof "$pkg"
stage=foreground
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity).*${pkg}"
adb exec-out screencap -p > evidence-android/startup.png
stage=crash-buffer
if adb logcat -d -b crash -v threadtime -T "$log_start" > /tmp/gabitama-launch-crash-private.txt 2>/dev/null; then
  if grep -Fq "Process: $pkg," /tmp/gabitama-launch-crash-private.txt; then
    echo 'Crash do aplicativo detectado; coletar logcat completo em ambiente privado.' >&2
    false
  fi
  echo '::notice title=Crash buffer::Sem crash do aplicativo no intervalo após abertura.'
else
  echo '::warning title=Crash buffer indisponível::Verificação de crash por logcat NÃO comprovada.'
fi
echo '::notice title=Smoke process alive::Processo vivo e Activity em primeiro plano após 8 segundos; UI ainda requer instrumentação.'
