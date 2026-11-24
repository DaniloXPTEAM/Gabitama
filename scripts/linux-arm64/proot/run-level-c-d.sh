#!/usr/bin/env bash
# Level C and D on a real Linux ARM64 host (not Android). Fixed commands only; no arbitrary input.
#   C: the original PRoot (built from the preserved upstream source) runs and executes the verified
#      Alpine 3.21.3 aarch64 userland.
#   D: a fixed diagnostic bootstrap inside that root (uname, release file, busybox applet).
# Passing this does NOT prove the Android app, bionic, Termux loaders, a PTY or a persistent shell.
set -euo pipefail
PROOT_SRC="third_party/tama-runtime/android/deps/proot/src"
PROOT="$PROOT_SRC/proot"
LOADER="$PROOT_SRC/loader/loader"
ROOTFS="${ROOTFS:?set ROOTFS to the extracted Alpine tree}"

echo "== Level C: PRoot binary"
test -x "$PROOT" || { echo "::error title=Level C::proot not built"; exit 1; }
test -x "$LOADER" || { echo "::error title=Level C::loader not built"; exit 1; }
machine=$(python3 -c "import struct,sys;d=open(sys.argv[1],'rb').read(20);print(struct.unpack('<H',d[18:20])[0])" "$PROOT")
test "$machine" = "183" || { echo "::error title=Level C::proot e_machine=$machine is not aarch64"; exit 1; }
echo "proot e_machine=$machine (aarch64) host=$(uname -m)"
test "$(uname -m)" = "aarch64" || { echo "::error title=Level C::host is not aarch64"; exit 1; }

echo "== Level C: run Alpine userland under PRoot"
export PROOT_LOADER="$PWD/$LOADER"
out=$("$PROOT" -r "$ROOTFS" -0 -w / /bin/busybox sh -c 'uname -m; cat /etc/alpine-release')
echo "$out"
echo "$out" | grep -qx "aarch64" || { echo "::error title=Level C::uname did not report aarch64"; exit 1; }
echo "$out" | grep -qx "3.21.3" || { echo "::error title=Level C::release is not 3.21.3"; exit 1; }
echo "::notice title=Level C::PRoot ran Alpine 3.21.3 aarch64 userland on a real aarch64 host"

echo "== Level D: fixed bootstrap diagnostic (no caller input)"
diag=$("$PROOT" -r "$ROOTFS" -0 -w / /bin/busybox sh -c 'echo "busybox=$(busybox echo ok)"; echo "musl=$(ls /lib | grep -c ld-musl)"; echo "bin_sh=$(readlink /bin/sh)"')
echo "$diag"
echo "$diag" | grep -qx "busybox=ok" || { echo "::error title=Level D::busybox applet failed"; exit 1; }
echo "::notice title=Level D::fixed diagnostic bootstrap OK"
