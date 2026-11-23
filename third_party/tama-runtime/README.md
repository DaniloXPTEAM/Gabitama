# Original Tama V2 PRoot build inputs (not packaged into GabiTama)

- Tama V2 commit `3711f582634721f0c428091489e3776674f90ff9`.
- `android/deps/proot`: exact tracked source tree from the upstream git submodule `https://github.com/nano-muse/proot` commit `4002f4bd3dfc38eb17ab6070d4154b2843eed452` (archived without Git metadata). PRoot's `COPYING` and individual source license headers are retained.
- `android/deps/build_proot.sh`, `android/deps/talloc/{replace.h,talloc.c,talloc.h}`: exact tracked upstream files. talloc files identify LGPL-3.0-or-later; the full LGPL text is provided alongside them. Static linking obligations require review for distribution.
- Two `libproot-loader*.so` files: exact vendored Tama bytes, with checksums verified by the original script. The upstream script says these are Termux builds and that the matching patches/source are not in its fork. Their corresponding source and complete license compliance have **not** been verified. These files are not included in the app; no release may depend on them until that issue is resolved.

The original script builds arm64 with Android NDK and writes outputs below this directory; those generated outputs are not committed. This isolated build is not a working or secured GabiTama terminal, and is not a device validation. Alpine rootfs and PersistentShell/TerminalSession/PRootKernel integration remain outstanding.
