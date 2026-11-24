#!/usr/bin/env bash
# Experimental two-package proof of concept (option 1). Runs on an emulator in CI with disposable builds.
# Fixed operations only; the Alpine rootfs comes from a CI-only debug asset (see the workflow).
# Passing this does NOT prove that PRoot, a terminal or Alpine execution work.
set -u
trap 'status=$?; echo "::error title=Helper PoC::Failure at line $LINENO (status $status)"' ERR
HELPER_APK=android/computer-helper/build/outputs/apk/debug/computer-helper-debug.apk
APP_APK=android/app/build/outputs/apk/debug/app-debug.apk
TEST_APK=android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for f in "$HELPER_APK" "$APP_APK" "$TEST_APK"; do test -f "$f" || { echo "::error title=Helper PoC::missing $f"; exit 1; }; done
adb install -r "$HELPER_APK" || { echo '::error title=Helper PoC setup::helper install failed'; exit 1; }
adb install -r "$APP_APK" || { echo '::error title=Helper PoC setup::app install failed'; exit 1; }
adb install -r "$TEST_APK" || { echo '::error title=Helper PoC setup::test install failed'; exit 1; }
adb shell pm clear com.novoagentehb.computerhelper >/dev/null || true
adb logcat -c || echo '::warning title=Helper PoC::Could not clear log buffer'
RUNNER=com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner
set +e
adb shell am instrument -w -r -e class com.novoagentehb.app.computer.helper.ComputerHelperPocAndroidTest \
  "$RUNNER" > /tmp/helper-poc.txt 2>&1
POC_STATUS=$?
adb shell am instrument -w -r -e class com.novoagentehb.app.computer.helper.ComputerRootfsProvisionAndroidTest \
  "$RUNNER" > /tmp/helper-rootfs.txt 2>&1
ROOTFS_STATUS=$?
adb shell am instrument -w -r -e class com.novoagentehb.app.computer.helper.ComputerProotRunAndroidTest \
  "$RUNNER" > /tmp/helper-proot.txt 2>&1
PROOT_STATUS=$?
set -e
# Diagnostic artefact (text only, CI-private): the fork trace the helper kept in its cache.
mkdir -p /tmp/poc-artifacts
adb shell run-as com.novoagentehb.computerhelper cat cache/proot-fork-trace.log > /tmp/poc-artifacts/proot-fork-trace.log 2>/dev/null || echo '::warning title=Helper PoC::fork trace not copied (run-as unavailable)'
adb logcat -d -s TamaIsolationFixture:V > /tmp/helper-poc-log.txt || true
# Readable summary for the job annotations (artefact download is not reachable from the sandbox).
TRACE=/tmp/poc-artifacts/proot-fork-trace.log
if [ -s "$TRACE" ]; then
  echo "::notice title=fork trace::bytes=$(wc -c < "$TRACE") lines=$(wc -l < "$TRACE")"
  BPF_COUNT=$(grep -c 'GABITAMA_BPF ' "$TRACE" || true)
  echo "::notice title=fork trace::bpf dump lines=$BPF_COUNT diag=${PROOT_DIAG_BPF:-0}"
  # Verdict first (the job shows only a few annotations per step): the installed filter evaluated per syscall.
  if [ "${BPF_COUNT:-0}" -gt 0 ]; then
    python3 scripts/android/computer-helper-poc/seccomp-filter-verdict.py "$TRACE" > /tmp/poc-artifacts/seccomp-filter-verdict.txt 2>&1 || true
    tr '\n' ' ' < /tmp/poc-artifacts/seccomp-filter-verdict.txt | sed 's/%/%25/g' | fold -w 900 | while IFS= read -r l; do echo "::notice title=seccomp verdict::$l"; done
  fi
  # Raw RET/JEQ lines of the first program (partial view; the artefact has the whole dump).
  grep -E 'GABITAMA_BPF [0-9]+ code=0x(6|15) ' "$TRACE" | head -60 | sed -E 's/GABITAMA_BPF ([0-9]+) code=0x([0-9a-f]+) jt=([0-9]+) jf=([0-9]+) k=0x([0-9a-f]+)/\1 op=\2 jt=\3 jf=\4 k=\5/' | cut -c1-120 | sed 's/%/%25/g' | while IFS= read -r l; do echo "::notice title=fork trace bpf::$l"; done
  grep -E 'SIGSYS|seccomp|GABITAMA_FORK|ENOSYS|clone|vfork|fork' "$TRACE" | head -10 | cut -c1-240 | sed 's/%/%25/g' | while IFS= read -r l; do echo "::notice title=fork trace::$l"; done
  if [ "${PROOT_DIAG_BPF:-0}" = "1" ] && [ "${BPF_COUNT:-0}" -eq 0 ]; then
    echo '::error title=fork trace::BPF dump absent in a diagnostic build (PROOT_DIAG_BPF=1 not reached PRoot)'
    BPF_STATUS=1
  fi
else
  echo "::notice title=fork trace::absent"
fi
export POC_STATUS ROOTFS_STATUS PROOT_STATUS BPF_STATUS=${BPF_STATUS:-0}
python3 - <<'PY'
import os, re
from pathlib import Path
checks = [
    ('Helper PoC', '/tmp/helper-poc.txt', int(os.environ['POC_STATUS']), 10),
    ('Helper rootfs', '/tmp/helper-rootfs.txt', int(os.environ['ROOTFS_STATUS']), 6),
    ('Helper PRoot run', '/tmp/helper-proot.txt', int(os.environ['PROOT_STATUS']), 5),
]
for line in Path('/tmp/helper-poc-log.txt').read_text(errors='replace').splitlines():
    if 'pocHelper:' in line:
        print('::notice title=Helper PoC::' + line.split('TamaIsolationFixture:', 1)[1].strip()[:400].replace('%', '%25'))
failed = int(os.environ['BPF_STATUS']) != 0
for title, path, status, expected in checks:
    out = Path(path).read_text(errors='replace')
    ok = status == 0 and re.search(rf'OK \({expected} tests\)', out) is not None and not re.search(r'\bFAILURES\b', out)
    if not ok:
        failed = True
        print(f'::error title={title}::FAIL (exit {status}); instrumentation output follows')
        for line in out.splitlines():
            if re.search(r'Error|Exception|FAIL|expected|Caused by|at com\.novoagentehb', line):
                print(f'::error title={title} diagnostic::' + line.strip()[:400].replace('%', '%25'))
    else:
        print(f'::notice title={title}::OK ({expected} tests)')
raise SystemExit(1 if failed else 0)
PY
