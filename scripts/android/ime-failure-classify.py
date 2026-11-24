#!/usr/bin/env python3
"""Classifies one `am instrument` log of the isolated IME test (CI diagnostic; never changes the verdict).

Usage: ime-failure-classify.py <test-name> <instrument-log>
Prints one `::notice`/`::error` line. Exit 0 only for PASS, so the caller keeps the original verdict.

Classes (first match wins):
  PASS        "OK (1 test)" and no FAILURES!!! block.
  NO_RESULT   no INSTRUMENTATION_RESULT/CODE line: the instrumentation did not finish (emulator or runner).
  CRASH       the app process crashed or the instrumentation was aborted.
  TIMEOUT     a Compose/UI wait expired (ComposeTimeoutException, "not met", "Timed out").
  ASSERTION   an assertion failed (AssertionError, expected/actual, a failed Compose assertion).
  FOCUS       RootViewWithoutFocusException was raised (also reported as root_focus_exception=true).
  UNKNOWN     a failure with none of the above markers.
Only short error lines are printed, never full logs.
"""
import re
import sys
from pathlib import Path

name, log_path = sys.argv[1], Path(sys.argv[2])
text = log_path.read_text(errors='replace') if log_path.is_file() else ''

if 'OK (1 test)' in text and 'FAILURES!!!' not in text:
    print(f'::notice title=Isolated API 34::{name} PASS class=PASS')
    raise SystemExit(0)

if not re.search(r'INSTRUMENTATION_(RESULT|CODE)', text):
    cls = 'NO_RESULT'
elif re.search(r'Process crashed|INSTRUMENTATION_ABORTED|has stopped|crashed', text):
    cls = 'CRASH'
elif re.search(r'ComposeTimeoutException|not met|Timed out|timed out', text):
    cls = 'TIMEOUT'
elif re.search(r'AssertionError|AssertionFailedError|expected:|Expected .* but|SemanticsNodeInteraction', text):
    cls = 'ASSERTION'
elif 'RootViewWithoutFocusException' in text:
    cls = 'FOCUS'
else:
    cls = 'UNKNOWN'

focus = 'RootViewWithoutFocusException' in text
print(f'::error title=Isolated API 34::{name} FAIL class={cls} root_focus_exception={focus} bytes={len(text)}')
# Short diagnostic lines only (no full stack, no user text beyond the test's own strings).
shown = 0
for line in text.splitlines():
    if shown >= 6:
        break
    if re.search(r'Error|Exception|expected|Caused by', line) and 'INSTRUMENTATION_STATUS: stack' not in line:
        print('::error title=Isolated API 34 diagnostic::' + line.strip()[:240].replace('%', '%25'))
        shown += 1
raise SystemExit(1)
