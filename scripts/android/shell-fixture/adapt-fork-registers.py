#!/usr/bin/env python3
"""Experimental x86-only adaptation of termux/proot e2d843e to the older Tama fork.

Never alters the tracked upstream tree or a production binary. The older fork attaches
children in new_child() rather than attach_child(); translate the upstream fix there.
"""
from pathlib import Path
import sys

root = Path(sys.argv[1])
def replace(path, old, new):
    p = root / path
    s = p.read_text()
    if s.count(old) != 1:
        raise SystemExit(f"Expected one match in {path}")
    p.write_text(s.replace(old, new, 1))

replace('tracee/tracee.h', '\tbool restore_original_regs_after_seccomp_event;\n',
        '\tbool restore_original_regs_after_seccomp_event;\n\tbool restore_regs_at_first_stop; /* termux/proot e2d843e */\n')
replace('tracee/reg.h', 'extern int push_regs(Tracee *tracee);\n',
        'extern int push_regs(Tracee *tracee);\nextern void restore_child_regs_after_seccomp(Tracee *child);\n')
replace('tracee/reg.c', 'word_t get_systrap_size(Tracee *tracee) {', '''/* Adapted from termux/proot e2d843ee6dab076ea0ed1ffd5d0ea12232185499. */
void restore_child_regs_after_seccomp(Tracee *child)
{
    Reg reg;
    if (!child->restore_regs_at_first_stop) return;
    child->restore_regs_at_first_stop = false;
    if (fetch_regs(child) < 0) return;
    for (reg = SYSARG_NUM; reg <= SYSARG_6; reg++)
        if (reg_offset[reg] != reg_offset[SYSARG_RESULT])
            poke_reg(child, reg, peek_reg(child, ORIGINAL_SECCOMP_REWRITE, reg));
    if (push_specific_regs(child, false) < 0)
        VERBOSE(child, 1, "failed to restore child registers");
}

word_t get_systrap_size(Tracee *tracee) {''')
replace('tracee/event.c', '#include "tracee/event.h"', '#include "tracee/reg.h"\n#include "tracee/event.h"')
replace('tracee/event.c', '\t\ttracee->running = false;\n', '''\t\ttracee->running = false;
\t\tif (WIFSTOPPED(tracee_status)) restore_child_regs_after_seccomp(tracee);
''')
replace('tracee/tracee.c', '''\tchild->verbose = parent->verbose;''', '''\t/* Same upstream register restoration, attached directly by this older fork. */
\tif (parent->restore_original_regs_after_seccomp_event) {
\t\tchild->_regs[ORIGINAL_SECCOMP_REWRITE] = parent->_regs[ORIGINAL_SECCOMP_REWRITE];
\t\tchild->restore_regs_at_first_stop = true;
\t}
\tchild->verbose = parent->verbose;''')
replace('tracee/tracee.c', '\t\tchild->sigstop = SIGSTOP_ALLOWED;\n',
        '\t\tchild->sigstop = SIGSTOP_ALLOWED;\n\t\trestore_child_regs_after_seccomp(child);\n')
