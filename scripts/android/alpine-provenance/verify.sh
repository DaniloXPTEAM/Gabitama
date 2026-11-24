#!/usr/bin/env bash
# Verifies the provenance of the official Alpine aarch64 minirootfs WITHOUT extracting
# or executing anything from it. Used only as CI evidence; nothing is bundled into an APK.
#
# Trust chain:
#   1. Tarball + .sha256 + .asc fetched from the official dl-cdn.alpinelinux.org mirror.
#   2. SHA-256 must equal the value pinned below (taken from the official .sha256 file).
#   3. Signing key fetched from https://alpinelinux.org/keys/ncopa.asc; its primary
#      fingerprint must equal the one published on https://alpinelinux.org/downloads/.
#   4. GPG signature of the tarball must verify against that key.
#   5. Tar entries are inspected with Python tarfile (no extraction): no absolute paths,
#      no '..' components, no device nodes, every link target stays inside the rootfs.
#   6. bin/busybox ELF header must be 64-bit little-endian aarch64 (e_machine 0xB7).
set -euo pipefail

ALPINE_VERSION="3.21.3"
ARCH="aarch64"
BASE_URL="https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/${ARCH}"
TARBALL="alpine-minirootfs-${ALPINE_VERSION}-${ARCH}.tar.gz"
PINNED_SHA256="ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea"
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

echo "[5-6] tar inspection (no extraction)"
python3 - "$WORK/$TARBALL" "$OUT_DIR/alpine-minirootfs-provenance.txt" "$ALPINE_VERSION" <<'PY'
import posixpath, sys, tarfile

tar_path, report_path, version = sys.argv[1], sys.argv[2], sys.argv[3]
problems = []
counts = {"file": 0, "dir": 0, "symlink": 0, "hardlink": 0, "other": 0}
busybox_header = None
release = None

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
        elf_ok = e_machine == 0xB7
        elf_note = f"e_machine=0x{e_machine:x} ({'aarch64' if elf_ok else 'NOT aarch64'})"
if not elf_ok:
    problems.append(f"bin/busybox architecture check failed: {elf_note}")
if release is None or release != version:
    problems.append(f"etc/alpine-release is {release!r}, expected {version!r}")

lines = [
    f"tarball: {tar_path.rsplit('/', 1)[-1]}",
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
echo "OK provenance report: $OUT_DIR/alpine-minirootfs-provenance.txt"
