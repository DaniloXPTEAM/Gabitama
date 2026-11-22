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
[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]
adb shell getprop ro.build.version.sdk
stage=install
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
echo '::notice title=INSTALL_PASS::APK instalado em AVD descartável'
stage=clear
adb shell pm clear "$pkg"
adb logcat -c
stage=start
adb shell am start -W -n "$pkg/$activity" | tee evidence-android/start.txt
stage=stability
sleep 8
adb shell pidof "$pkg"
stage=foreground
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity).*${pkg}"
adb exec-out screencap -p > evidence-android/startup.png
stage=crash-buffer
if adb logcat -d -b crash | grep -F "Process: $pkg,"; then
  echo 'Crash do aplicativo detectado; coletar logcat completo em ambiente privado.' >&2
  false
fi
echo '::notice title=Smoke process alive::Processo vivo e Activity em primeiro plano após 8 segundos; UI ainda requer instrumentação.'
