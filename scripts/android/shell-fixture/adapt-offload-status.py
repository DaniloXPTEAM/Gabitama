#!/usr/bin/env python3
"""Experimental fixture-only PRoot fix: preserve offload exit status.

The vendored upstream source is left unchanged. No guest text is interpolated
into the fixed shell program; path and status are separate argv entries.
"""
from pathlib import Path
import sys

path = Path(sys.argv[1]) / 'extension/native_offload/native_offload.c'
s = path.read_text()
start = s.index('/* Write a NULL-terminated argv=["cat", <tmpfile>, NULL]')
end = s.index('\nstatic void handle_execve_enter', start)
replacement = r'''/* Execute a fixed script with the response path and status as argv, not code. */
static int rewrite_as_cat(Tracee *tracee, Reg filename_reg, Reg argv_reg,
                          const char *tmpfile, int32_t exit_code)
{
    static const char shell[] = "/bin/sh";
    static const char option[] = "-c";
    static const char script[] = "/bin/cat \"$1\" || exit $?; exit \"$2\"";
    static const char name[] = "native-offload";
    char code[4];
    snprintf(code, sizeof(code), "%u", (unsigned int) exit_code & 255U);
    const char *args[] = { shell, option, script, name, tmpfile, code };
    word_t values[7] = { 0 };
    int status = set_sysarg_data(tracee, shell, sizeof(shell), filename_reg);
    if (status < 0) return status;
    for (size_t i = 0; i < 6; ++i) {
        size_t len = strlen(args[i]) + 1;
        values[i] = alloc_mem(tracee, len);
        if (values[i] == 0) return -EFAULT;
        status = write_data(tracee, values[i], args[i], len);
        if (status < 0) return status;
    }
    word_t argv = alloc_mem(tracee, sizeof(values));
    if (argv == 0) return -EFAULT;
    status = write_data(tracee, argv, values, sizeof(values));
    if (status < 0) return status;
    poke_reg(tracee, argv_reg, argv);
    return 0;
}
'''
s = s[:start] + replacement + s[end:]
old = 'rewrite_as_cat(tracee, filename_reg, argv_reg, rsp.tmpfile)'
assert s.count(old) == 1
s = s.replace(old, 'rewrite_as_cat(tracee, filename_reg, argv_reg, rsp.tmpfile, rsp.exit_code)')
path.write_text(s)
