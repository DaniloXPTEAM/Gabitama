package com.novoagentehb.computerhelper.contract

/**
 * Contract between the GabiTama app and the experimental computer helper package.
 * Shared as source by both Android modules (no Android dependency here).
 *
 * Only fixed operations exist. There is no shell, no arbitrary path and no execution.
 */
object HelperContract {
    const val HELPER_PACKAGE = "com.novoagentehb.computerhelper"
    const val ACTION_BIND = "com.novoagentehb.computerhelper.action.BIND"
    const val PERMISSION = "com.novoagentehb.computerhelper.permission.USE_HELPER"
    const val DESCRIPTOR = "com.novoagentehb.computerhelper.IHelper"

    // Transaction codes (fixed, read-only except WRITE).
    const val TX_STATUS = 1
    const val TX_WRITE = 2
    const val TX_READ = 3
    const val TX_SHA256 = 4
    const val TX_ACCESS_PROBE = 5
    const val TX_PROVISION_START = 6
    const val TX_PROVISION_STATUS = 7
    const val TX_PROVISION_CANCEL = 8

    // Fixed PRoot runs (experimental, debug/test builds only). The caller sends only an operation id.
    const val TX_PROOT_START = 10
    const val TX_PROOT_STATUS = 11
    const val TX_PROOT_CANCEL = 12
    const val PROOT_OP_VERSION = 1
    const val PROOT_OP_HOST_SLEEP = 2
    /** Fixed guest program: /bin/sh of the Alpine x86_64 TEST rootfs. No caller-supplied argv. */
    const val PROOT_OP_ALPINE_X86_PROBE = 3
    /**
     * Diagnostic only (CI emulator): the same Alpine x86_64 guest runs `/bin/true` from a shell, which
     * forks. PRoot verbose output goes to a private file; the reply carries only a filtered summary.
     */
    const val PROOT_OP_ALPINE_X86_FORK_TRACE = 4
    const val GUEST_FORK_BEGIN = "GABITAMA_FORK_BEGIN"
    const val GUEST_FORK_RC = "GABITAMA_FORK_RC="
    const val PROOT_TRACE_VERBOSE = "4"
    const val PROOT_MAX_OUTPUT_BYTES = 4096
    const val PROOT_VERSION_DEADLINE_MS = 10_000L
    const val PROOT_SLEEP_DEADLINE_MS = 5_000L
    const val PROOT_ALPINE_PROBE_DEADLINE_MS = 20_000L

    // Test-only Alpine x86_64 variant (CI emulator proof). Separate from the aarch64 rootfs above:
    // its own asset, directory and marker. Never replaces the aarch64 installation or its history.
    const val TX_TEST_X86_PROVISION_START = 16
    const val TX_TEST_X86_PROVISION_STATUS = 17
    const val TX_TEST_X86_PROVISION_CANCEL = 18
    const val ALPINE_X86_ASSET = "alpine-minirootfs-x86_64.bin"
    // Official dl-cdn.alpinelinux.org v3.21/releases/x86_64 .sha256 for alpine-minirootfs-3.21.3-x86_64.tar.gz
    const val ALPINE_X86_SHA256 = "1a694899e406ce55d32334c47ac0b2efb6c06d7e878102d1840892ad44cd5239"
    const val ROOTFS_X86_DIR = "alpine-rootfs-x86_64-test"
    /** Marker strings written by the fixed guest probe; the test asserts them. */
    const val GUEST_PROBE_BEGIN = "GABITAMA_GUEST_BEGIN"

    // Fixed Alpine rootfs provisioning (experimental, debug/test builds only). No arguments accepted.
    const val ALPINE_VERSION = "3.21.3"
    // Asset name without a compressed-file extension: the Android build stored "*.tar.gz" as "*.tar".
    // The bytes are still the official .tar.gz, and ALPINE_SHA256 is checked over those exact bytes.
    const val ALPINE_ASSET = "alpine-minirootfs-aarch64.bin"
    const val ALPINE_SHA256 = "ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea"
    const val ROOTFS_DIR = "alpine-rootfs"
    const val ROOTFS_MAX_ENTRIES = 20_000
    const val ROOTFS_MAX_BYTES = 256L * 1024 * 1024
    const val ROOTFS_MIN_FREE_BYTES = 256L * 1024 * 1024

    // Result codes written as the first int of every reply.
    const val CODE_OK = 0
    const val CODE_DENIED = 1
    const val CODE_BAD_PATH = 2
    const val CODE_TOO_LARGE = 3
    const val CODE_BUSY = 4
    const val CODE_NOT_FOUND = 5
    const val CODE_QUOTA = 6
    const val CODE_TIMEOUT = 7
    const val CODE_IO = 8
    const val CODE_BAD_REQUEST = 9
    const val CODE_NOT_INSTALLED = 10

    // Limits. Enforced by the helper; the client applies the same bounds before sending.
    const val MAX_PAYLOAD_BYTES = 64 * 1024
    const val MAX_FILE_BYTES = 256 * 1024
    const val MAX_TREE_BYTES = 4 * 1024 * 1024
    const val MAX_TREE_ENTRIES = 256
    const val MAX_CONCURRENT_OPS = 2
    const val OP_TIMEOUT_MS = 3_000L
    const val MAX_PATH_SEGMENTS = 4
    const val MAX_SEGMENT_LENGTH = 64

    /** Only these caller packages may use the helper (signed with the same key via the permission). */
    val ALLOWED_CLIENT_PACKAGES: Set<String> = setOf(
        "com.novoagentehb.app",
        "com.novoagentehb.app.debug",
    )

    /** Fixed probe targets, relative to the caller's private data directory. Metadata only, never content. */
    val PROBE_TARGETS: List<String> = listOf("files/", "databases/", "shared_prefs/")
}
