#!/usr/bin/env bash
set -uo pipefail
# Only run on the disposable CI emulator. Preserve the original test status.
(cd android && ./gradlew connectedDebugAndroidTest --no-daemon --stacktrace)
status=$?
mkdir -p evidence-android
if adb shell test -d /sdcard/Android/data/com.novoagentehb.app.debug/files/test-screenshots; then
  adb pull /sdcard/Android/data/com.novoagentehb.app.debug/files/test-screenshots evidence-android/ || status=1
fi
python3 - <<'PY'
import glob,xml.etree.ElementTree as E
files=glob.glob('android/app/build/outputs/androidTest-results/connected/**/*.xml',recursive=True)
roots=[E.parse(f).getroot() for f in files]
counts={k:sum(int(r.get(k,0)) for r in roots) for k in ('tests','failures','errors','skipped')}
print('::notice title=Android tests::'+ ' '.join(f'{k}={v}' for k,v in counts.items()))
for root in roots:
    for case in root.iter('testcase'):
        failure=case.find('failure')
        if failure is None: failure=case.find('error')
        if failure is not None:
            # Do not put UI hierarchy, test data, or exception messages into public annotations.
            print('::error title=Android test failed::'+case.get('classname','')+'.'+case.get('name',''))
PY
exit "$status"
