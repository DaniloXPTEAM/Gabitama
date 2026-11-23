package com.novoagentehb.computerhelper.contract

/**
 * Pure validation of relative paths inside the helper's own tree.
 * Rejects absolute paths, traversal, empty or oversized segments, and anything outside the allowed charset.
 */
object TreePath {
    private val SEGMENT = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    fun validate(relative: String): Int {
        if (relative.isEmpty() || relative.length > 256) return HelperContract.CODE_BAD_PATH
        if (relative.startsWith("/") || relative.contains('\\') || relative.contains('\u0000')) {
            return HelperContract.CODE_BAD_PATH
        }
        val segments = relative.split('/')
        if (segments.size > HelperContract.MAX_PATH_SEGMENTS) return HelperContract.CODE_BAD_PATH
        for (segment in segments) {
            if (segment == "." || segment == "..") return HelperContract.CODE_BAD_PATH
            if (segment.length > HelperContract.MAX_SEGMENT_LENGTH) return HelperContract.CODE_BAD_PATH
            if (!SEGMENT.matches(segment)) return HelperContract.CODE_BAD_PATH
        }
        return HelperContract.CODE_OK
    }
}
