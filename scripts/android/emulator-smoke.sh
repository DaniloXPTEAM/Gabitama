#!/usr/bin/env bash
set -euo pipefail
pkg=com.novoagentehb.app.debug
activity=com.novoagentehb.app.app.MainActivity
mkdir -p evidence-android
adb devices -l
[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]
adb shell getprop ro.build.version.sdk
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
# AVD descartável exclusivamente; nunca aplicar a aparelho de usuário.
adb shell pm clear "$pkg"
adb logcat -c
adb shell am start -W -n "$pkg/$activity" | tee evidence-android/start.txt
sleep 8
adb shell pidof "$pkg"
adb shell dumpsys activity activities | grep -E "(mResumedActivity|topResumedActivity).*${pkg}"
adb exec-out screencap -p > evidence-android/startup.png
# Não publicar log bruto: pode conter dados sensíveis. Teste somente detecção no AVD limpo.
if adb logcat -d -b crash | grep -F "Process: $pkg,"; then
  echo 'Crash do aplicativo detectado; coletar logcat completo em ambiente privado.' >&2
  exit 1
fi
