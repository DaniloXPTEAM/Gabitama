"""Turns a failed Gradle log into GitHub annotations (used by the helper PoC workflow)."""
import re
import sys

lines = open(sys.argv[1], errors="replace").read().splitlines()
emitted = 0
for i, line in enumerate(lines):
    if re.match(r"^(e: |error: |\* What went wrong:|Execution failed for task|> )", line):
        for value in lines[i:i + 2]:
            print("::error title=Helper PoC build::" + value.replace("%", "%25")[:800])
            emitted += 1
        if emitted >= 30:
            break
if emitted == 0:
    print("::error title=Helper PoC build::no Gradle error line found; tail follows")
    for value in lines[-15:]:
        print("::error title=Helper PoC build tail::" + value.replace("%", "%25")[:400])
