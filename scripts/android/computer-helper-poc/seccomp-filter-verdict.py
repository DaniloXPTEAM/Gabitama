#!/usr/bin/env python3
"""Diagnostic: evaluates the seccomp BPF program dumped by the CI PRoot copy (GABITAMA_BPF lines).

Reads the FIRST program of a trace (lines `GABITAMA_BPF <i> code=0x.. jt=.. jf=.. k=0x..`), then runs it
in a small classic-BPF interpreter for x86_64 syscall numbers. It reports the action each syscall receives.
It changes nothing on the device and is not a fix. Output is short: one line per syscall of interest,
plus a count of actions over 0..461.

Usage: seccomp-filter-verdict.py <trace-file>      |  seccomp-filter-verdict.py --selftest
"""
import re
import sys
from pathlib import Path

ARCH_X86_64 = 0xC000003E
LINE = re.compile(r'GABITAMA_BPF (\d+) code=0x([0-9a-f]+) jt=(\d+) jf=(\d+) k=0x([0-9a-f]+)')
SYSCALLS = {0: 'read', 1: 'write', 2: 'open', 4: 'stat', 56: 'clone', 57: 'fork', 58: 'vfork',
            59: 'execve', 61: 'wait4', 157: 'prctl', 257: 'openat', 262: 'newfstatat', 435: 'clone3'}
ACTIONS = {0x7FFF0000: 'ALLOW', 0x7FF00000: 'TRACE', 0x00030000: 'TRAP', 0x00050000: 'ERRNO',
           0x7FFC0000: 'LOG', 0x80000000: 'KILL_PROCESS', 0x00000000: 'KILL_THREAD'}


def action_of(value):
    name = ACTIONS.get(value & 0xFFFF0000, f'0x{value & 0xFFFF0000:x}')
    data = value & 0xFFFF
    return f'{name}({data})' if name in ('TRACE', 'TRAP', 'ERRNO') else name


def first_program(text):
    prog = []
    for m in LINE.finditer(text):
        index = int(m.group(1))
        if index == 0 and prog:
            break
        if index != len(prog):
            raise SystemExit(f'verdict: non-contiguous BPF index {index} (expected {len(prog)})')
        prog.append((int(m.group(2), 16), int(m.group(3)), int(m.group(4)), int(m.group(5), 16)))
    return prog


def run(prog, nr, arch=ARCH_X86_64):
    """Classic BPF over struct seccomp_data: nr at offset 0, arch at offset 4. Other loads read 0."""
    a = x = 0
    pc = 0
    steps = 0
    while pc < len(prog):
        steps += 1
        if steps > 4096:
            return 'LOOP'
        code, jt, jf, k = prog[pc]
        cls = code & 0x07
        if cls == 0x00:  # LD
            if code & 0xE0 == 0x20:  # ABS
                a = nr if k == 0 else arch if k == 4 else 0
            elif code & 0xE0 == 0x00:  # IMM
                a = k
            else:
                return 'UNSUPPORTED'
            pc += 1
        elif cls == 0x01:  # LDX
            x = k
            pc += 1
        elif cls in (0x02, 0x03):  # ST / STX (scratch memory not modelled)
            pc += 1
        elif cls == 0x04:  # ALU
            src = x if code & 0x08 else k
            op = code & 0xF0
            if op == 0x00: a = (a + src) & 0xFFFFFFFF
            elif op == 0x10: a = (a - src) & 0xFFFFFFFF
            elif op == 0x20: a = (a * src) & 0xFFFFFFFF
            elif op == 0x40: a |= src
            elif op == 0x50: a &= src
            elif op == 0x60: a = (a << src) & 0xFFFFFFFF
            elif op == 0x70: a >>= src
            else: return 'UNSUPPORTED'
            pc += 1
        elif cls == 0x05:  # JMP
            op = code & 0xF0
            if op == 0x00:  # JA
                pc = pc + 1 + k
                continue
            src = x if code & 0x08 else k
            if op == 0x10: taken = a == src
            elif op == 0x20: taken = a > src
            elif op == 0x30: taken = a >= src
            elif op == 0x40: taken = (a & src) != 0
            else: return 'UNSUPPORTED'
            pc = pc + 1 + (jt if taken else jf)
        elif cls == 0x06:  # RET
            return action_of(a if code & 0x18 == 0x10 else k)
        elif cls == 0x07:  # MISC
            if code & 0xF8 == 0x80: a = x
            else: x = a
            pc += 1
        else:
            return 'UNSUPPORTED'
    return 'FELL_OFF'


def selftest():
    prog = [(0x20, 0, 0, 0), (0x15, 0, 1, 57), (0x06, 0, 0, 0x00030000), (0x06, 0, 0, 0x7FFF0000)]
    assert run(prog, 57) == 'TRAP(0)', run(prog, 57)
    assert run(prog, 1) == 'ALLOW', run(prog, 1)
    assert run(prog, 1, arch=0x40000003) == 'ALLOW'
    jmp = [(0x20, 0, 0, 4), (0x15, 1, 0, ARCH_X86_64), (0x06, 0, 0, 0x80000000), (0x06, 0, 0, 0x7FFF0000)]
    assert run(jmp, 9) == 'ALLOW' and run(jmp, 9, arch=1) == 'KILL_PROCESS'
    print('selftest ok')


def main():
    if sys.argv[1:] == ['--selftest']:
        selftest()
        return
    text = Path(sys.argv[1]).read_text(errors='replace')
    prog = first_program(text)
    if not prog:
        print('verdict: no BPF program in trace')
        raise SystemExit(1)
    print(f'verdict: program_len={len(prog)} arch=x86_64')
    for nr, name in sorted(SYSCALLS.items()):
        print(f'verdict: {name}({nr})={run(prog, nr)}')
    counts = {}
    for nr in range(0, 462):
        verdict = run(prog, nr).split('(')[0]
        counts[verdict] = counts.get(verdict, 0) + 1
    print('verdict: over nr 0..461 ' + ' '.join(f'{k}={v}' for k, v in sorted(counts.items())))


if __name__ == '__main__':
    main()
