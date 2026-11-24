#!/usr/bin/env python3
"""Convert AndroidJUnitRunner protocol into a minimal sanitized JUnit XML.
Fail closed on incomplete, ignored, duplicate or failed runs; raw output stays private.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

# Classes run by their own workflow with extra APKs; excluded from this shared suite (see connected-tests.sh).
EXCLUDED_CLASSES = {
    'com.novoagentehb.app.computer.helper.ComputerHelperPocAndroidTest',
    'com.novoagentehb.app.computer.helper.ComputerRootfsProvisionAndroidTest',
    'com.novoagentehb.app.computer.helper.ComputerProotRunAndroidTest',
}
EXCLUDED_FILES = {os.path.basename(c.replace('.', '/')) + '.kt' for c in EXCLUDED_CLASSES}

EXC = r'(?:[a-zA-Z_$][\w$]*\.)*[A-Za-z_$][\w$]*(?:Exception|Error)'

def describe_failure(stack):
    """Sanitized diagnosis: first exception, first cause, the setUp/tearDown phase and the first project frames.
    Messages are never copied; only class names, our own phase markers and project frame locations."""
    first = re.search(r'\b' + EXC + r'\b', stack)
    cause = re.search(r'Caused by: (' + EXC + r')', stack)
    phase = re.search(r'setUp fase=([A-Za-z0-9_.]+)', stack)
    teardown = re.search(r'tearDown fases=([A-Za-z0-9_.:,]+)', stack)
    if phase:
        where = 'setUp'
    elif teardown or re.search(r'\.tearDown\(', stack):
        where = 'tearDown'
    elif re.search(r'\.setUp\(', stack):
        where = 'setUp'
    else:
        where = 'body'
    frames = re.findall(r'at (com\.novoagentehb\.app\.[\w.$]+\([\w.$]+(?::\d+)?\))', stack)
    lines = [
        'where=' + where,
        'exception=' + (first.group(0) if first else 'UnknownError'),
        'cause=' + (cause.group(1) if cause else 'none'),
        'phase=' + (phase.group(1) if phase else (teardown.group(1) if teardown else 'none')),
    ]
    lines += ['at ' + frame for frame in frames[:3]]
    return '\n'.join(lines)

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
                details[key] = describe_failure(stack)
        else:
            raise SystemExit('::error title=Instrumentation protocol::Unknown test status')
        status = {}
        last_key = None
    elif last_key == 'stack' and not line.startswith('INSTRUMENTATION_'):
        status[last_key] += '\n' + line

def source_test_count(root='android/app/src/androidTest'):
    """Number of @Test methods in the instrumentation sources: the suite's own declared size."""
    total = 0
    ignored = 0
    for directory, _, files in os.walk(root):
        for name in files:
            if name in EXCLUDED_FILES:
                continue
            if name.endswith(('.kt', '.java')):
                with open(os.path.join(directory, name), encoding='utf-8', errors='replace') as handle:
                    text = handle.read()
                    total += len(re.findall(r'^\s*@Test\b', text, re.M))
                    ignored += len(re.findall(r'^\s*@Ignore\b', text, re.M))
    return total, ignored

# Derived from the sources, not hard-coded, so a new test cannot make the run look incomplete
# (or an old count mask a missing test). Skips are allowed only when they match the @Ignore declarations in source.
expected, declared_ignored = source_test_count()
print(f'::notice title=Instrumentation suite::declared @Test methods={expected} declared @Ignore={declared_ignored}')
if (len(started) != expected or set(completed) != started or
        not re.search(r'^INSTRUMENTATION_CODE: -1\s*$', raw, re.M) or
        re.search(r'^INSTRUMENTATION_FAILED:', raw, re.M)):
    print(f'::error title=Instrumentation protocol::Incomplete run; expected={expected} started={len(started)} completed={len(completed)}')
    unfinished = sorted(k for k in started if k not in completed)
    for cls, name in unfinished[:3]:
        print(f'::error title=Instrumentation abort::interrupted while running {cls.rsplit(".", 1)[-1]}.{name}')
    markers = {
        'process_crashed': len(re.findall(r'Process crashed|has died|Instrumentation run failed', raw)),
        'code_minus1_seen': bool(re.search(r'^INSTRUMENTATION_CODE: -1\s*$', raw, re.M)),
        'instrumentation_failed_line': bool(re.search(r'^INSTRUMENTATION_FAILED:', raw, re.M)),
    }
    print('::notice title=Instrumentation termination::' + ' '.join(f'{k}={v}' for k, v in markers.items()))
    # Sanitized: only the known termination phrase and class-like exception tokens; never the message text.
    for phrase in ('Process crashed', 'has died', 'Instrumentation run failed'):
        if phrase in raw:
            tokens = sorted(set(re.findall(r'\b(?:[a-zA-Z_$][\w$]*\.)+[A-Za-z_$][\w$]*(?:Exception|Error)\b', raw)))[:3]
            print(f'::error title=Instrumentation termination phrase::{phrase} classes={",".join(tokens) or "none"}')
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
if failures or skipped != declared_ignored:
    print(f'::error title=Instrumentation result::failures={failures} skipped={skipped} declared_ignore={declared_ignored}')
    sys.exit(1)
sys.exit(0)
