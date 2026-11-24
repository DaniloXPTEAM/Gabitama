#!/usr/bin/env bash
# Verifies the provenance of the official Alpine aarch64 minirootfs WITHOUT extracting
# or executing anything from it. Nothing is bundled into a published APK. CI may copy the verified
# tarball to a debug-only test asset via KEEP_TARBALL_TO (never committed, never uploaded).
#
# Trust chain:
#   1. Tarball + .sha256 + .asc fetched from the official dl-cdn.alpinelinux.org mirror.
#   2. SHA-256 must equal the value pinned below (taken from the official .sha256 file).
#   3. Signing key fetched from https://alpinelinux.org/keys/ncopa.asc; its primary
#      fingerprint must equal the one published on https://alpinelinux.org/downloads/.
#   4. GPG signature of the tarball must verify against that key.
#   5. Tar entries are inspected with Python tarfile (no extraction): no absolute paths,
#      no '..' components, no device nodes, every link target stays inside the rootfs.
#   6. bin/busybox ELF header must be 64-bit little-endian for the selected arch (aarch64 0xB7, x86_64 0x3E).
set -euo pipefail

ALPINE_VERSION="3.21.3"
# ALPINE_ARCH selects the variant: aarch64 (product rootfs) or x86_64 (CI emulator test only).
ARCH="${ALPINE_ARCH:-aarch64}"
case "$ARCH" in
  aarch64)
    # Official dl-cdn .sha256 for alpine-minirootfs-3.21.3-aarch64.tar.gz
    PINNED_SHA256="ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea"
    EXPECTED_EM="0xB7"; EXPECTED_NAME="aarch64" ;;
  x86_64)
    # Official dl-cdn .sha256 for alpine-minirootfs-3.21.3-x86_64.tar.gz (fetched from the same mirror)
    PINNED_SHA256="1a694899e406ce55d32334c47ac0b2efb6c06d7e878102d1840892ad44cd5239"
    EXPECTED_EM="0x3E"; EXPECTED_NAME="x86_64" ;;
  *) echo "FAIL unsupported ALPINE_ARCH=$ARCH" >&2; exit 1 ;;
esac
# The aarch64 report keeps its historical name (consumed by verify-alpine-provenance.yml).
if [ "$ARCH" = "aarch64" ]; then REPORT_NAME="alpine-minirootfs-provenance.txt"; else REPORT_NAME="alpine-minirootfs-provenance-$ARCH.txt"; fi
BASE_URL="https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/${ARCH}"
TARBALL="alpine-minirootfs-${ALPINE_VERSION}-${ARCH}.tar.gz"
PUBLISHED_FPR="0482D84022F52DF1C4E7CD43293ACD0907D9495A"
KEY_URL="https://alpinelinux.org/keys/ncopa.asc"

OUT_DIR="${OUT_DIR:-$(mktemp -d)}"
mkdir -p "$OUT_DIR"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
export GNUPGHOME="$WORK/gnupg"
mkdir -m 700 "$GNUPGHOME"

echo "[1] download official artifacts"
curl -fsSL --retry 3 -o "$WORK/$TARBALL" "$BASE_URL/$TARBALL"
curl -fsSL --retry 3 -o "$WORK/$TARBALL.asc" "$BASE_URL/$TARBALL.asc"
curl -fsSL --retry 3 -o "$WORK/ncopa.asc" "$KEY_URL"

echo "[2] pinned SHA-256"
ACTUAL_SHA256="$(sha256sum "$WORK/$TARBALL" | cut -d' ' -f1)"
if [ "$ACTUAL_SHA256" != "$PINNED_SHA256" ]; then
  echo "FAIL sha256 mismatch: expected $PINNED_SHA256 got $ACTUAL_SHA256" >&2
  exit 1
fi
echo "OK sha256 $ACTUAL_SHA256"

echo "[3] key fingerprint"
gpg --batch --quiet --import "$WORK/ncopa.asc"
KEY_FPR="$(gpg --batch --with-colons --fingerprint | awk -F: '$1=="fpr"{print $10; exit}')"
if [ "$KEY_FPR" != "$PUBLISHED_FPR" ]; then
  echo "FAIL key fingerprint: expected $PUBLISHED_FPR got $KEY_FPR" >&2
  exit 1
fi
echo "OK key fingerprint $KEY_FPR"

echo "[4] GPG signature"
gpg --batch --status-file "$WORK/status" --verify "$WORK/$TARBALL.asc" "$WORK/$TARBALL"
if ! grep -q "^\[GNUPG:\] VALIDSIG ${PUBLISHED_FPR}" "$WORK/status"; then
  echo "FAIL signature not made by the published key" >&2
  exit 1
fi
echo "OK VALIDSIG from published key"
if [ -n "${KEEP_TARBALL_TO:-}" ]; then
  # Only after every check above passed. Callers must keep the copy out of Git and out of artifacts.
  cp "$WORK/$TARBALL" "$KEEP_TARBALL_TO"
  echo "OK kept verified tarball at $KEEP_TARBALL_TO (not for distribution)"
fi

echo "[5-6] tar inspection (no extraction)"
python3 - "$WORK/$TARBALL" "$OUT_DIR/$REPORT_NAME" "$ALPINE_VERSION" "$EXPECTED_EM" "$EXPECTED_NAME" <<'PY'
import posixpath, sys, tarfile

tar_path, report_path, version = sys.argv[1], sys.argv[2], sys.argv[3]
expected_em, expected_name = int(sys.argv[4], 16), sys.argv[5]
problems = []
counts = {"file": 0, "dir": 0, "symlink": 0, "hardlink": 0, "other": 0}
busybox_header = None
release = None
apk_db = None

with tarfile.open(tar_path, "r:gz") as tf:
    for m in tf:
        name = m.name
        if name.startswith("/") or "\x00" in name:
            problems.append(f"absolute or invalid name: {name!r}")
        parts = [p for p in name.split("/") if p not in ("", ".")]
        if ".." in parts:
            problems.append(f"parent component: {name!r}")
        if m.isdev():
            problems.append(f"device node: {name!r}")
        if m.issym():
            counts["symlink"] += 1
            target = posixpath.normpath(posixpath.join(posixpath.dirname(name), m.linkname))
            if m.linkname.startswith("/"):
                target = posixpath.normpath(m.linkname.lstrip("/"))
            if target.startswith("..") or target.startswith("/"):
                problems.append(f"symlink escapes rootfs: {name!r} -> {m.linkname!r}")
        elif m.islnk():
            counts["hardlink"] += 1
            target = posixpath.normpath(m.linkname.lstrip("/"))
            if target.startswith(".."):
                problems.append(f"hardlink escapes rootfs: {name!r} -> {m.linkname!r}")
        elif m.isfile():
            counts["file"] += 1
        elif m.isdir():
            counts["dir"] += 1
        else:
            counts["other"] += 1
        norm = posixpath.normpath(name[2:] if name.startswith("./") else name)
        if norm == "bin/busybox" and m.isfile():
            f = tf.extractfile(m)
            busybox_header = f.read(20) if f else b""
        if norm == "lib/apk/db/installed" and m.isfile():
            f = tf.extractfile(m)
            apk_db = f.read().decode("utf-8", "replace") if f else ""
        if norm == "etc/alpine-release" and m.isfile():
            f = tf.extractfile(m)
            release = (f.read().decode("utf-8", "replace").strip() if f else "")

elf_ok = False
elf_note = "bin/busybox not found"
if busybox_header is not None:
    if busybox_header[:4] != b"\x7fELF":
        elf_note = "not an ELF file"
    elif busybox_header[4] != 2 or busybox_header[5] != 1:
        elf_note = "not ELF64 little-endian"
    else:
        e_machine = int.from_bytes(busybox_header[18:20], "little")
        elf_ok = e_machine == expected_em
        elf_note = f"e_machine=0x{e_machine:x} ({expected_name if elf_ok else 'NOT ' + expected_name})"
if not elf_ok:
    problems.append(f"bin/busybox architecture check failed: {elf_note}")
if release is None or release != version:
    problems.append(f"etc/alpine-release is {release!r}, expected {version!r}")

# Package inventory from the APK database inside the tarball: name, version and declared license.
packages = []
for record in (apk_db or "").split("\n\n"):
    fields = dict(line.split(":", 1) for line in record.splitlines() if ":" in line)
    if "P" in fields:
        packages.append((fields["P"], fields.get("V", "?"), fields.get("L", "?")))
# One annotation for the whole inventory: per-package annotations exhaust the per-job annotation quota.
print("::notice title=Alpine packages::" + "; ".join(f"{name} {version} license={license_id}" for name, version, license_id in packages))
lines = [
    f"tarball: {tar_path.rsplit('/', 1)[-1]}",
    f"packages: {len(packages)}",
    *[f"  package {name} {version} license={license_id}" for name, version, license_id in packages],
    f"alpine-release: {release}",
    f"bin/busybox: {elf_note}",
    "entries: " + ", ".join(f"{k}={v}" for k, v in counts.items()),
    f"problems: {len(problems)}",
]
lines += [f"  - {p}" for p in problems]
with open(report_path, "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines) + "\n")
print("\n".join(lines))
if problems:
    sys.exit(1)
PY
echo "OK provenance report: $OUT_DIR/$REPORT_NAME"
