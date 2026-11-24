#!/usr/bin/env python3
"""Diagnostic-only instrumentation of a DISPOSABLE copy of the original PRoot source.

Applied by build-original.sh only when PROOT_DIAG_BPF=1 (CI emulator diagnostics; never a product build).
Adds two runtime log lines, both printed to stderr of the tracee/PRoot process:
  - GABITAMA_BPF <index> code=.. jt=.. jf=.. k=..   one line per instruction of the seccomp program
    installed by set_seccomp_filters() (printed just before prctl(PR_SET_SECCOMP)).
  - GABITAMA_SIGSYS code=.. syscall=.. arch=..        siginfo fields of each SYS_SECCOMP SIGSYS.
It changes no filter decision and no handler result. It exists to explain the observed ENOSYS.
Usage: diag-seccomp-patch.py <path-to-proot/src>
"""
import sys
from pathlib import Path

src = Path(sys.argv[1])


def patch(rel, pairs):
    path = src / rel
    text = path.read_text(encoding='utf-8')
    for old, new in pairs:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f'diag patch: {rel}: expected 1 match, found {count}: {old[:60]!r}')
        text = text.replace(old, new)
    path.write_text(text, encoding='utf-8')


patch('syscall/seccomp.c', [
    ('#include <assert.h>        /* assert(3), */\n',
     '#include <assert.h>        /* assert(3), */\n#include <stdio.h>         /* fprintf(3) (diagnostic only) */\n'
     '#include <stdlib.h>        /* getenv(3) (diagnostic only) */\n'
     ),
    ('\tstatus = prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program);\n',
     '\tif (getenv("PROOT_DIAG_BPF") != NULL) {\n'
     '\t\tfor (size_t di = 0; di < program.len; di++)\n'
     '\t\t\tfprintf(stderr, "GABITAMA_BPF %zu code=0x%x jt=%u jf=%u k=0x%x\\n", di,\n'
     '\t\t\t\t(unsigned) program.filter[di].code, (unsigned) program.filter[di].jt,\n'
     '\t\t\t\t(unsigned) program.filter[di].jf, (unsigned) program.filter[di].k);\n'
     '\t}\n'
     '\tstatus = prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program);\n'
     '\tif (getenv("PROOT_DIAG_BPF") != NULL) {\n'
     '\t\tFILE *gabitama_status = fopen("/proc/self/status", "r");\n'
     '\t\tchar gabitama_line[256];\n'
     '\t\twhile (gabitama_status != NULL && fgets(gabitama_line, sizeof(gabitama_line), gabitama_status) != NULL)\n'
     '\t\t\tif (strncmp(gabitama_line, "Seccomp", 7) == 0)\n'
     '\t\t\t\tfprintf(stderr, "GABITAMA_SECCOMP_STATUS %s", gabitama_line);\n'
     '\t\tif (gabitama_status != NULL)\n'
     '\t\t\tfclose(gabitama_status);\n'
     '\t}\n'),
])

patch('tracee/event.c', [
    ('#include <assert.h>     /* assert(3), */\n',
     '#include <assert.h>     /* assert(3), */\n#include <stdio.h>      /* fprintf(3) (diagnostic only) */\n'),
    ('\t\t\tptrace(PTRACE_GETSIGINFO, tracee->pid, NULL, &siginfo);\n\t\t\tif (siginfo.si_code == SYS_SECCOMP) {\n',
     '\t\t\tptrace(PTRACE_GETSIGINFO, tracee->pid, NULL, &siginfo);\n'
     '\t\t\tif (getenv("PROOT_DIAG_BPF") != NULL)\n'
     '\t\t\t\tfprintf(stderr, "GABITAMA_SIGSYS code=%d syscall=%ld arch=0x%x\\n",\n'
     '\t\t\t\t\tsiginfo.si_code, (long) siginfo.si_syscall, (unsigned) siginfo.si_arch);\n'
     '\t\t\tif (siginfo.si_code == SYS_SECCOMP) {\n'),
])
print('diag patch applied: seccomp.c, event.c')
