#!/usr/bin/env bash
set -uo pipefail
# Collect while instrumentation is active: Gradle may uninstall the target and its
# external files after tests. Only the explicitly named synthetic AVD evidence is copied.
mkdir -p evidence-android/test-screenshots
remote=/sdcard/Android/data/com.novoagentehb.app.debug/files/test-screenshots
collector() {
  while :; do
    if adb shell test -d "$remote" 2>/dev/null; then
      adb pull "$remote/." evidence-android/test-screenshots/ >/tmp/gabitama-screenshot-pull-private.txt 2>&1 || :
    fi
    sleep 1
  done
}
collector & collector_pid=$!
trap 'kill "$collector_pid" 2>/dev/null || :; wait "$collector_pid" 2>/dev/null || :' EXIT
# No Gradle invocation here: install and run the exact shared test APK.
status=0
adb install -r candidate/app-debug-androidTest.apk || status=1
test_installed=$(adb shell pm path com.novoagentehb.app.debug.test | tr -d '\r' | sed -n 's/^package://p')
if [ -z "$test_installed" ] || ! adb exec-out cat "$test_installed" > /tmp/gabitama-installed-test.apk || ! cmp candidate/app-debug-androidTest.apk /tmp/gabitama-installed-test.apk; then
  echo '::error title=Test APK::Installed test APK does not match shared binary.'
  status=1
else
  echo "::notice title=Installed Test APK SHA256::$(sha256sum /tmp/gabitama-installed-test.apk | cut -d' ' -f1)"
fi
adb shell pm list instrumentation | grep -F 'com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner (target=com.novoagentehb.app.debug)' >/dev/null || status=1
if [ "$status" -eq 0 ]; then
  # The computer-helper PoC needs its separate helper APK, installed only by its own workflow
  # (test-computer-helper-poc.yml). This shared suite does not install it, so exclude that class here.
  adb shell am instrument -w -r -e notClass com.novoagentehb.app.computer.helper.ComputerHelperPocAndroidTest,com.novoagentehb.app.computer.helper.ComputerRootfsProvisionAndroidTest com.novoagentehb.app.debug.test/androidx.test.runner.AndroidJUnitRunner > /tmp/gabitama-instrumentation-ci.log 2>&1 || status=1
  python3 scripts/android/parse-instrumentation.py || status=1
fi
# Capture files once more before stopping the collector.
adb pull "$remote/." evidence-android/test-screenshots/ >/tmp/gabitama-screenshot-final-private.txt 2>&1 || :
kill "$collector_pid" 2>/dev/null || :
wait "$collector_pid" 2>/dev/null || :
trap - EXIT
adb logcat -d -b crash -v brief > /tmp/gabitama-test-crash-private.txt 2>/dev/null || echo '::warning title=Test crash log::Indisponível; diagnóstico de crash limitado.'
# Public diagnostics contain only app test evidence messages (path, dimensions, UID, size).
adb logcat -d -s GabiTestEvidence:I '*:S' > /tmp/gabitama-screenshot-log-private.txt 2>/dev/null || :
python3 scripts/android/validate-screenshots.py || status=1
python3 - <<'PY' || status=1
import glob,xml.etree.ElementTree as E
files=glob.glob('android/app/build/outputs/androidTest-results/connected/**/*.xml',recursive=True)
if not files:
    print('::error title=Android tests::Nenhum XML de instrumentação encontrado.')
    raise SystemExit(1)
roots=[E.parse(f).getroot() for f in files]
counts={k:sum(int(r.get(k,0)) for r in roots) for k in ('tests','failures','errors','skipped')}
import os
os.makedirs('evidence-android', exist_ok=True)
safe=E.Element('testsuites')
for root in roots:
    suite=E.SubElement(safe, 'testsuite', {k:root.get(k,'0') for k in ('tests','failures','errors','skipped')})
    for case in root.iter('testcase'):
        item=E.SubElement(suite, 'testcase', {'classname':case.get('classname',''), 'name':case.get('name','')})
        if case.find('failure') is not None or case.find('error') is not None:
            E.SubElement(item, 'failure', {'message':'Details intentionally omitted; see sanitized annotations.'})
        elif case.find('skipped') is not None:
            E.SubElement(item, 'skipped')
E.ElementTree(safe).write('evidence-android/test-results-sanitized.xml', encoding='utf-8', xml_declaration=True)
print('::notice title=Android tests::'+ ' '.join(f'{k}={v}' for k,v in counts.items()))
import re
lines=[]
for root in roots:
    for case in root.iter('testcase'):
        failure=case.find('failure')
        if failure is None: failure=case.find('error')
        name=case.get('classname','')+'.'+case.get('name','')
        lines.append(('FAIL ' if failure is not None else 'PASS ')+name)
        if failure is not None:
            text=(failure.get('message','')+'\n'+(failure.text or ''))
            checkpoints=re.findall(r'checkpoint=[A-Za-z0-9_]+ inicio_clickable=\d+ home=\d+ goals=\d+ lifecycle=[A-Z]+', text)
            lines.extend('Navigation: '+x for x in checkpoints[:3])
            types=re.findall(r'(?:[a-zA-Z_$][\w$]*\.)*[\w$]*(?:Exception|Error)', text)
            if types: lines.append('Exception type: '+types[0])
            for term, category in [('was already used', 'duplicate key'), ('CLEARTEXT', 'Android cleartext policy'), ('expected:<RESUMED>', 'lifecycle state mismatch'), ('not in the resumed state', 'activity not resumed')]:
                if term.lower() in text.lower(): lines.append('Diagnostic: '+category)
            testframes=re.findall(r'(?:at )?(com\.novoagentehb\.app\.[^\n() ]+\([^\n)]*\))',text)
            lines.extend('frame '+x for x in testframes[:8] if x not in lines)
            if 'java.lang.IllegalArgumentException' in text:
                # Emit only a fixed set of diagnostic phrases; never copy arbitrary values.
                for phrase in ('No activities found', 'already exists', 'No value passed', 'must not be null', 'Failed requirement', 'Cannot round NaN', 'Only finite values', 'Navigation destination', 'Cannot navigate'):
                    if phrase.lower() in text.lower(): lines.append('Reason category: '+phrase)
            frames=re.findall(r'at (com\.novoagentehb\.app\.[\w$.$]+\([\w.$]+(?::\d+)?\))', text)
            lines.extend('at '+frame for frame in frames[:8])
            for term in ('METAS','MEMÓRIA','ARQUIVOS','Permissões','Quarto da Gabi','DIAGNÓSTICO LOCAL','Diagnóstico','Ajustes','Avançar','Adicionar meta','Continuar','Gabi · HB','Avisos & Recibos'):
                if term in text: lines.append('Mentioned selector: '+term)
try:
    crash=open('/tmp/gabitama-test-crash-private.txt',encoding='utf-8',errors='replace').read()
    if 'com.novoagentehb.app.debug' in crash:
        if re.search(r'Key .* was already used', crash, re.I):
            print('::error title=Crash cause::Compose duplicate lazy-list key detected')
        out=[]
        for line in crash.splitlines():
            exc=re.search(r'(?:Caused by: )?([\w.$]+(?:Exception|Error))(?::|\s|$)',line)
            frame=re.search(r'\bat (com\.novoagentehb\.app\.[\w.$]+\([\w.$]+(?::\d+)?\))',line)
            if exc: out.append(('Caused by: ' if 'Caused by:' in line else 'Exception: ')+exc.group(1))
            elif frame: out.append('at '+frame.group(1))
        if out: print('::error title=Sanitized test crash::'+'%0A'.join(out[:60]))
except OSError:
    pass
try:
    log=open('/tmp/gabitama-instrumentation-ci.log',encoding='utf-8',errors='replace').read()
    diagnostics=re.findall(r'GABIDIAG checkpoint=[A-Za-z0-9_]+ inicio_clickable=\d+ home=\d+ goals=\d+ lifecycle=[A-Z]+', log)
    if diagnostics: print('::notice title=Navigation diagnostics::'+'%0A'.join(diagnostics))
    progress=re.findall(r'Tests? \d+/\d+ completed[^\n]*', log)
    if progress: print('::notice title=Android runner progress::'+progress[-1].replace('%','%25'))
except OSError:
    pass
if lines:
    print('::notice title=Android test case summary::'+'%0A'.join(lines))

PY
exit "$status"
