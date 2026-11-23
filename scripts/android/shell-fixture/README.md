# Experimental x86_64 fixture (not a production PRoot)

Tama's original PRoot on an x86_64 Android AVD successfully ran shell builtins,
but external commands failed with `can't fork: Function not implemented`. Android's
outer seccomp rejects x86_64 `fork`, used directly by Alpine musl. Termux/proot
commits `9bd53079d25737ac739e1210cd490c49377d0cbe` and
`e2d843ee6dab076ea0ed1ffd5d0ea12232185499` address fork-to-clone and restoration
of child registers. `termux-fork-sigsys.patch` is the former commit's exact diff;
`adapt-fork-registers.py` adapts the latter to the older Tama fork's child setup.
`fork_regs.c` is the latter commit's original test. License: PRoot GPL-2.0-or-later;
see `third_party/tama-runtime/android/deps/proot/COPYING` and source headers.

This backport is applied only to a disposable build copy to investigate x86 test
coverage. It is not used for the arm64 build, not packaged in production, and not
claimed safe or equivalent to the complete upstream fix. In particular, the old
fork lacks later upstream fixes for unusual fork event ordering. Its test result
cannot validate physical ARM devices or app-level isolation.

The fixture also applies `adapt-offload-status.py` to its disposable copy:
the original extension receives an offload exit code but rewrites the command
to `cat` without returning it. The experimental adaptation uses a fixed shell
script with separate argv for output path and status; the fixture checks exit 7.
This is not applied to production and needs ARM and adversarial review.
