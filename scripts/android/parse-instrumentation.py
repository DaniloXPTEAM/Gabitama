#!/usr/bin/env python3
"""Convert AndroidJUnitRunner protocol into a minimal sanitized JUnit XML.
Fail closed on incomplete, ignored, duplicate or failed runs; raw output stays private.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

raw = open('/tmp/gabitama-instrumentation-ci.log', encoding='utf-8', errors='replace').read().replace('\r', '')
status, started, completed = {}, set(), {}
for line in raw.splitlines():
    if line.startswith('INSTRUMENTATION_STATUS: '):
        key, sep, value = line[len('INSTRUMENTATION_STATUS: '):].partition('=')
        if sep:
            status[key] = value
    elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
        code = int(line.split(': ', 1)[1])
        cls, name = status.get('class', ''), status.get('test', '')
        if not re.fullmatch(r'com\.novoagentehb\.app\.[A-Za-z0-9_.$]+', cls) or not re.fullmatch(r'[A-Za-z0-9_$]+', name):
            raise SystemExit('::error title=Instrumentation protocol::Missing or invalid test identity')
        key = (cls, name)
        if code == 1:
            if key in started: raise SystemExit('::error title=Instrumentation protocol::Duplicate start')
            started.add(key)
        elif code in (0, -1, -2, -3, -4):
            if key not in started or key in completed: raise SystemExit('::error title=Instrumentation protocol::Unexpected result')
            completed[key] = code
        else:
            raise SystemExit('::error title=Instrumentation protocol::Unknown test status')
        status = {}

expected = int(os.environ.get('GABITAMA_EXPECTED_TESTS', '14'))
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
    if code in (-1, -2): ET.SubElement(case, 'failure', message='Details retained only in private runner log')
    elif code in (-3, -4): ET.SubElement(case, 'skipped')
out = 'android/app/build/outputs/androidTest-results/connected/TEST-instrumentation.xml'
os.makedirs(os.path.dirname(out), exist_ok=True)
ET.ElementTree(suite).write(out, encoding='utf-8', xml_declaration=True)
sys.exit(1 if failures or skipped else 0)
