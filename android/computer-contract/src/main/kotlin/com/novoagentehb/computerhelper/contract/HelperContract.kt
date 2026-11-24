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
