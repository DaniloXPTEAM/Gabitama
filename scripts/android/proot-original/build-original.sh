#!/usr/bin/env bash
# Builds the ORIGINAL Tama V2 PRoot (third_party/tama-runtime/android/deps) for one Android ABI.
#
# The repository copy is never modified. The build runs in a disposable copy with these changes, every
# one written to the manifest:
#   1. ABI and NDK triple are parameters (the original script is arm64-only);
#   2. the ELF machine check accepts the chosen ABI;
#   3. x86_64 only: HAS_LOADER_32BIT= (no 32-bit guest loader; the emulator test runs 64-bit guests);
#   4. the original install step is skipped. It only installs the pinned Termux loaders, whose origin
#      is not proven, and it aborts for any other loader. The loader used here is built from the same
#      source by the PRoot make target.
#
# Output (outside the repository, not for distribution): libproot.so, libproot-loader.so, manifest.txt.
#
# Usage: ANDROID_NDK_HOME=<ndk> build-original.sh <x86_64|arm64-v8a> <out-dir>
set -euo pipefail

ABI="${1:?usage: build-original.sh <x86_64|arm64-v8a> <out-dir>}"
OUT="${2:?usage: build-original.sh <x86_64|arm64-v8a> <out-dir>}"
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to an installed NDK}"

case "$ABI" in
  x86_64)    TRIPLE="x86_64-linux-android";  ELF_MACHINE="x86-64";  MAKE_EXTRA="HAS_LOADER_32BIT=" ;;
  arm64-v8a) TRIPLE="aarch64-linux-android"; ELF_MACHINE="aarch64"; MAKE_EXTRA="" ;;
  *) echo "::error title=PRoot build::unsupported ABI $ABI" >&2; exit 2 ;;
esac

REPO="$(git rev-parse --show-toplevel)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cp -a "$REPO/third_party/tama-runtime/android/." "$WORK/"

python3 - "$WORK/deps/build_proot.sh" "$ABI" "$TRIPLE" "$ELF_MACHINE" <<'PY'
import sys
path, abi, triple, elf = sys.argv[1:]
s = open(path, encoding='utf-8').read()

def rep(old, new):
    global s
    assert s.count(old) == 1, old
    s = s.replace(old, new)

rep('ANDROID_ABI="arm64-v8a"', f'ANDROID_ABI="{abi}"')
rep('NDK_TRIPLE="aarch64-linux-android"', f'NDK_TRIPLE="{triple}"')
rep("grep -q 'aarch64'", f"grep -qi '{elf}'")
rep('    install_asset\n    verify_artifacts\n', '')
rep('        make \\\n            CC="$CC"', '        make \\\n            ${MAKE_EXTRA:-} \\\n            CC="$CC"')
open(path, 'w', encoding='utf-8').write(s)
PY

# Diagnostic copy only (CI emulator): seccomp BPF and SIGSYS dump. Off by default; never a product build.
DIAG_NOTE="none"
if [ "${PROOT_DIAG_BPF:-0}" = "1" ]; then
  python3 "$REPO/scripts/android/proot-original/diag-seccomp-patch.py" "$WORK/deps/proot/src"
  DIAG_NOTE="bpf-seccomp-dump (disposable copy, CI diagnostic only)"
fi
export DIAG_NOTE
export MAKE_EXTRA ANDROID_NDK_HOME
export GIT_CEILING_DIRECTORIES="$(dirname "$WORK")"
(cd "$WORK" && bash deps/build_proot.sh)

SRC="$WORK/deps/proot/src"
test -x "$SRC/proot" || { echo "::error title=PRoot build::proot binary missing"; exit 1; }
test -x "$SRC/loader/loader" || { echo "::error title=PRoot build::loader missing"; exit 1; }

mkdir -p "$OUT"
install -m 0755 "$SRC/proot" "$OUT/libproot.so"
install -m 0755 "$SRC/loader/loader" "$OUT/libproot-loader.so"

# Verification: ELF machine, dynamic dependencies, no vendored Termux loader and no unexpected libtalloc.
machine_of() { readelf -h "$1" | awk -F: '/Machine/ {sub(/^ +/, "", $2); print $2}'; }
needed_of() { readelf -d "$1" | awk '/NEEDED/ {gsub(/[\[\]]/, "", $NF); print $NF}' | tr '\n' ' '; }
for f in libproot.so libproot-loader.so; do
  m="$(machine_of "$OUT/$f")"
  case "$ABI:$m" in
    x86_64:*"X86-64"*|arm64-v8a:*"AArch64"*) ;;
    *) echo "::error title=PRoot build::$f machine '$m' does not match $ABI"; exit 1 ;;
  esac
done
NEEDED="$(needed_of "$OUT/libproot.so")"
case " $NEEDED " in *" libtalloc.so "*) echo "::error title=PRoot build::libtalloc is dynamically linked"; exit 1 ;; esac
TERMUX_SHA256="44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04"
if [ "$(sha256sum "$OUT/libproot-loader.so" | awk '{print $1}')" = "$TERMUX_SHA256" ]; then
  echo "::error title=PRoot build::loader equals the vendored Termux build"; exit 1
fi

NDK_REV="$(awk -F' = ' '/Pkg.Revision/ {print $2}' "$ANDROID_NDK_HOME/source.properties" 2>/dev/null || echo unknown)"
{
  echo "abi=$ABI triple=$TRIPLE ndk=$NDK_REV"
  echo "source=third_party/tama-runtime/android/deps/proot (upstream nano-muse/proot 4002f4bd3dfc38eb17ab6070d4154b2843eed452)"
  echo "makeExtra=${MAKE_EXTRA:-none}"
  echo "diag=$DIAG_NOTE"
  echo "libproot.so machine=$(machine_of "$OUT/libproot.so") sha256=$(sha256sum "$OUT/libproot.so" | awk '{print $1}') bytes=$(stat -c %s "$OUT/libproot.so")"
  echo "libproot-loader.so machine=$(machine_of "$OUT/libproot-loader.so") sha256=$(sha256sum "$OUT/libproot-loader.so" | awk '{print $1}') bytes=$(stat -c %s "$OUT/libproot-loader.so")"
  echo "libproot.so NEEDED=$NEEDED"
  echo "termuxLoaderUsed=false"
} > "$OUT/manifest.txt"

while IFS= read -r line; do
  printf '::notice title=PRoot build::%s\n' "$line"
done < "$OUT/manifest.txt"
