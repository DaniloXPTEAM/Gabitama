#!/usr/bin/env python3
"""Convert AndroidJUnitRunner protocol into a minimal sanitized JUnit XML.
Fail closed on incomplete, ignored, duplicate or failed runs; raw output stays private.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

# Classes run by their own workflow with extra APKs; excluded from this shared suite (see connected-tests.sh).
EXCLUDED_CLASSES = {'com.novoagentehb.app.computer.helper.ComputerHelperPocAndroidTest'}
EXCLUDED_FILES = {os.path.basename(c.replace('.', '/')) + '.kt' for c in EXCLUDED_CLASSES}

raw = open('/tmp/gabitama-instrumentation-ci.log', encoding='utf-8', errors='replace').read().replace('\r', '')
status, started, completed, details = {}, set(), {}, {}
last_key = None
for line in raw.splitlines():
    if line.startswith('INSTRUMENTATION_STATUS: '):
        key, sep, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
        if sep:
            status[key] = value
            last_key = key
    elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
        code = int(line.split(': ', 1)[1])
        cls, name = status.get('class', ''), status.get('test', '')
        if not re.fullmatch(r'com\.novoagentehb\.app\.[A-Za-z0-9_.$]+', cls) or not re.fullmatch(r'[A-Za-z0-9_$]+', name):
            raise SystemExit('::error title=Instrumentation protocol::Missing or invalid test identity')
        if cls in EXCLUDED_CLASSES:
            raise SystemExit('::error title=Instrumentation protocol::Excluded class ran in shared suite')
        key = (cls, name)
        if code == 1:
            if key in started: raise SystemExit('::error title=Instrumentation protocol::Duplicate start')
            started.add(key)
        elif code in (0, -1, -2, -3, -4):
            if key not in started or key in completed: raise SystemExit('::error title=Instrumentation protocol::Unexpected result')
            completed[key] = code
            if code in (-1, -2):
                stack = status.get('stack', '')
                exception = re.search(r'\b(?:[a-zA-Z_$][\w$]*\.)*[A-Za-z_$][\w$]*(?:Exception|Error)\b', stack)
                frames = re.findall(r'at (com\.novoagentehb\.app\.[\w.$]+\([\w.$]+(?::\d+)?\))', stack)
                details[key] = ((exception.group(0) if exception else 'UnknownError') + '\n' + '\n'.join('at ' + frame for frame in frames[:8]))
        else:
            raise SystemExit('::error title=Instrumentation protocol::Unknown test status')
        status = {}
        last_key = None
    elif last_key == 'stack' and not line.startswith('INSTRUMENTATION_'):
        status[last_key] += '\n' + line

def source_test_count(root='android/app/src/androidTest'):
    """Number of @Test methods in the instrumentation sources: the suite's own declared size."""
    total = 0
    for directory, _, files in os.walk(root):
        for name in files:
            if name in EXCLUDED_FILES:
                continue
            if name.endswith(('.kt', '.java')):
                with open(os.path.join(directory, name), encoding='utf-8', errors='replace') as handle:
                    total += len(re.findall(r'^\s*@Test\b', handle.read(), re.M))
    return total

# Derived from the sources, not hard-coded, so a new test cannot make the run look incomplete
# (or an old count mask a missing test). An @Ignore'd test would still be reported as skipped.
expected = source_test_count()
print(f'::notice title=Instrumentation suite::declared @Test methods={expected}')
if (len(started) != expected or set(completed) != started or
        not re.search(r'^INSTRUMENTATION_CODE: -1\s*$', raw, re.M) or
        re.search(r'^INSTRUMENTATION_FAILED:', raw, re.M)):
    print(f'::error title=Instrumentation protocol::Incomplete run; expected={expected} started={len(started)} completed={len(completed)}')
    sys.exit(1)
failures = sum(code in (-1, -2) for code in completed.values())
skipped = sum(code in (-3, -4) for code in completed.values())
suite = ET.Element('testsuite', tests=str(len(completed)), failures=str(failures), errors='0', skipped=str(skipped))
for (cls, name), code in sorted(completed.items()):
    case = ET.SubElement(suite, 'testcase', classname=cls, name=name)
    if code in (-1, -2): ET.SubElement(case, 'failure', message='Sanitized exception and test frames').text = details.get((cls, name), '')
    elif code in (-3, -4): ET.SubElement(case, 'skipped')
out = 'android/app/build/outputs/androidTest-results/connected/TEST-instrumentation.xml'
os.makedirs(os.path.dirname(out), exist_ok=True)
ET.ElementTree(suite).write(out, encoding='utf-8', xml_declaration=True)
sys.exit(1 if failures or skipped else 0)
