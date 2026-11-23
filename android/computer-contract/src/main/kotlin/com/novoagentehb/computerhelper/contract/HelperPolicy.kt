package com.novoagentehb.computerhelper.contract

/**
 * Pure authorization and bounds policy for helper calls. The Android service supplies the facts
 * (caller packages from the Binder UID, permission grant, request sizes) and acts on the decision.
 */
object HelperPolicy {
    data class Decision(val code: Int, val reason: String)

    fun authorize(callerPackages: Collection<String>, permissionGranted: Boolean): Decision {
        if (!permissionGranted) return Decision(HelperContract.CODE_DENIED, "permission-not-granted")
        if (callerPackages.isEmpty()) return Decision(HelperContract.CODE_DENIED, "no-caller-package")
        val allowed = callerPackages.any { it in HelperContract.ALLOWED_CLIENT_PACKAGES }
        return if (allowed) Decision(HelperContract.CODE_OK, "allowed")
        else Decision(HelperContract.CODE_DENIED, "package-not-allowlisted")
    }

    fun checkPayload(bytes: Int): Int =
        if (bytes > HelperContract.MAX_PAYLOAD_BYTES) HelperContract.CODE_TOO_LARGE else HelperContract.CODE_OK

    fun checkFile(bytes: Long): Int =
        if (bytes > HelperContract.MAX_FILE_BYTES) HelperContract.CODE_TOO_LARGE else HelperContract.CODE_OK

    /** Quota check after the write: the tree including the new content must stay within limits. */
    fun checkQuota(existingBytes: Long, existingEntries: Int, replacedBytes: Long, newBytes: Long, newEntry: Boolean): Int {
        val total = existingBytes - replacedBytes + newBytes
        val entries = existingEntries + if (newEntry) 1 else 0
        return if (total > HelperContract.MAX_TREE_BYTES || entries > HelperContract.MAX_TREE_ENTRIES) {
            HelperContract.CODE_QUOTA
        } else HelperContract.CODE_OK
    }
}
