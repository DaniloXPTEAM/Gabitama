#!/usr/bin/env bash
set -uo pipefail
# Only run on the disposable CI emulator. Preserve the original test status.
(cd android && ./gradlew connectedDebugAndroidTest --no-daemon --stacktrace) 2>&1 | tee /tmp/gabitama-instrumentation-ci.log
status=${PIPESTATUS[0]}
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
            types=re.findall(r'(?:[a-zA-Z_$][\w$]*\.)*[\w$]*(?:Exception|Error)', text)
            if types: lines.append('Exception type: '+types[0])
            if 'java.lang.IllegalArgumentException' in text:
                # Emit only a fixed set of diagnostic phrases; never copy arbitrary values.
                for phrase in ('No activities found', 'already exists', 'No value passed', 'must not be null', 'Failed requirement', 'Cannot round NaN', 'Only finite values', 'Navigation destination', 'Cannot navigate'):
                    if phrase.lower() in text.lower(): lines.append('Reason category: '+phrase)
            frames=re.findall(r'at (com\.novoagentehb\.app\.[\w$.$]+\([\w.$]+(?::\d+)?\))', text)
            lines.extend('at '+frame for frame in frames[:8])
            for term in ('METAS','MEMÓRIA','ARQUIVOS','Permissões','Quarto da Gabi','DIAGNÓSTICO LOCAL','Diagnóstico','Ajustes','Avançar','Adicionar meta','Continuar','Gabi · HB','Avisos & Recibos'):
                if term in text: lines.append('Mentioned selector: '+term)
try:
    log=open('/tmp/gabitama-instrumentation-ci.log',encoding='utf-8',errors='replace').read()
    progress=re.findall(r'Tests? \d+/\d+ completed[^\n]*', log)
    if progress: print('::notice title=Android runner progress::'+progress[-1].replace('%','%25'))
except OSError:
    pass
if lines:
    print('::notice title=Android test case summary::'+'%0A'.join(lines))

PY
exit "$status"
