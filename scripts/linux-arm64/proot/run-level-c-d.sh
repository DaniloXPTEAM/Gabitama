#!/usr/bin/env bash
# Level C and D on a real Linux ARM64 host (not Android). Fixed commands only; no arbitrary input.
#   C: the original PRoot (built from the preserved upstream source) runs and executes the verified
#      Alpine 3.21.3 aarch64 userland.
#   D: a fixed diagnostic bootstrap inside that root (uname, release file, busybox applet).
# Passing this does NOT prove the Android app, bionic, Termux loaders, a PTY or a persistent shell.
set -euo pipefail
trap 'echo "::error title=Level C/D::failed at line $LINENO: $BASH_COMMAND (exit $?)"' ERR
PROOT_SRC="${PROOT_SRC:-third_party/tama-runtime/android/deps/proot/src}"
PROOT="$PROOT_SRC/proot"
LOADER="$PROOT_SRC/loader/loader"
ROOTFS="${ROOTFS:?set ROOTFS to the extracted Alpine tree}"

echo "== Level C: PRoot binary"
echo "::notice title=Host::uname=$(uname -m) kernel=$(uname -r) proot=$PROOT loader=$LOADER"
test -x "$PROOT" || { echo "::error title=Level C::proot not built"; exit 1; }
test -x "$LOADER" || { echo "::error title=Level C::loader not built"; exit 1; }
machine=$(python3 -c "import struct,sys;d=open(sys.argv[1],'rb').read(20);print(struct.unpack('<H',d[18:20])[0])" "$PROOT")
test "$machine" = "183" || { echo "::error title=Level C::proot e_machine=$machine is not aarch64"; exit 1; }
echo "proot e_machine=$machine (aarch64) host=$(uname -m)"
test "$(uname -m)" = "aarch64" || { echo "::error title=Level C::host is not aarch64"; exit 1; }

echo "== Level C: run Alpine userland under PRoot"
run_proot() {
  # Capture stdout AND stderr; on failure publish the PRoot diagnostics (set -e would otherwise hide them).
  local title="$1"; shift
  local log rc=0
  log=$(mktemp)
  "$PROOT" "$@" >"$log" 2>&1 || rc=$?
  if [ "$rc" -ne 0 ]; then
    # run_proot runs inside $(...): its stdout is captured, so diagnostics must go to stderr.
    { echo "::group::PRoot output ($title, exit $rc)"; cat "$log"; echo "::endgroup::"; } >&2
    echo "::error title=$title::PRoot exited $rc: $(tail -c 700 "$log" | tr '\n' ' ')" >&2
    rm -f "$log"; exit 1
  fi
  cat "$log"; rm -f "$log"
}
export PROOT_LOADER="$(cd "$(dirname "$LOADER")" && pwd)/$(basename "$LOADER")"
echo "proot version: $("$PROOT" --version 2>&1 || true)"
# Diagnostic matrix (informational, does not gate): isolates the tracee SIGSEGV seen on the first run.
probe() {
  # probe NAME ROOT CMD... (env assignments first, passed via env)
  local name="$1" root="$2"; shift 2
  local log rc=0
  log=$(mktemp)
  env "$@" "$PROOT" -r "$root" -0 -w / /bin/sh -c 'echo ok-from-root' >"$log" 2>&1 || rc=$?
  echo "::notice title=Diag $name::exit=$rc tail=$(tail -c 300 "$log" | tr '\n' ' ')" >&2
  rm -f "$log"
}
probe "alpine-default" "$ROOTFS" PROOT_LOADER="$PROOT_LOADER"
probe "alpine-no-seccomp" "$ROOTFS" PROOT_LOADER="$PROOT_LOADER" PROOT_NO_SECCOMP=1
probe "alpine-verbose3" "$ROOTFS" PROOT_LOADER="$PROOT_LOADER" PROOT_VERBOSE=3
probe "host-root-default" / PROOT_LOADER="$PROOT_LOADER"
probe "host-root-no-seccomp" / PROOT_LOADER="$PROOT_LOADER" PROOT_NO_SECCOMP=1
# Loader itself: headers and a direct run (no PRoot). Loader B = same loader without --rosegment, default ld.
echo "::notice title=Loader A headers::$(readelf -lW "$LOADER" 2>/dev/null | grep -E 'LOAD|GNU_STACK' | tr -s ' ' | tr '\n' ' ' | cut -c1-500)" >&2
direct_rc=0; "$LOADER" /bin/true >/dev/null 2>&1 || direct_rc=$?
echo "::notice title=Diag loader-A-direct::exit=$direct_rc" >&2
if [ -n "${LOADER_B:-}" ] && [ -x "$LOADER_B" ]; then
  echo "::notice title=Loader B headers::$(readelf -lW "$LOADER_B" 2>/dev/null | grep -E 'LOAD|GNU_STACK' | tr -s ' ' | tr '\n' ' ' | cut -c1-500)" >&2
  probe "alpine-loaderB" "$ROOTFS" PROOT_LOADER="$LOADER_B"
  probe "host-root-loaderB" / PROOT_LOADER="$LOADER_B"
else
  echo "::notice title=Loader B::not built" >&2
fi
out=$(run_proot "Level C" -r "$ROOTFS" -0 -w / /bin/busybox sh -c 'uname -m; cat /etc/alpine-release')
echo "$out"
echo "$out" | grep -qx "aarch64" || { echo "::error title=Level C::uname did not report aarch64"; exit 1; }
echo "$out" | grep -qx "3.21.3" || { echo "::error title=Level C::release is not 3.21.3"; exit 1; }
echo "::notice title=Level C::PRoot ran Alpine 3.21.3 aarch64 userland on a real aarch64 host"

echo "== Level D: fixed bootstrap diagnostic (no caller input)"
diag=$(run_proot "Level D" -r "$ROOTFS" -0 -w / /bin/busybox sh -c 'echo "busybox=$(busybox echo ok)"; echo "musl=$(ls /lib | grep -c ld-musl)"; echo "bin_sh=$(readlink /bin/sh)"')
echo "$diag"
echo "$diag" | grep -qx "busybox=ok" || { echo "::error title=Level D::busybox applet failed"; exit 1; }
echo "::notice title=Level D::fixed diagnostic bootstrap OK"
