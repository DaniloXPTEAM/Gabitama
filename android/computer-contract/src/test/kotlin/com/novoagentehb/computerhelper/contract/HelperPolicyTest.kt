package com.novoagentehb.computerhelper.contract

import org.junit.Assert.assertEquals
import org.junit.Test

class HelperPolicyTest {
    @Test fun unauthorizedPackageIsDenied() {
        assertEquals(HelperContract.CODE_DENIED, HelperPolicy.authorize(listOf("com.evil.app"), true).code)
    }

    @Test fun missingPermissionIsDeniedEvenForAllowlistedPackage() {
        assertEquals(HelperContract.CODE_DENIED, HelperPolicy.authorize(listOf("com.novoagentehb.app.debug"), false).code)
    }

    @Test fun emptyCallerIsDenied() {
        assertEquals(HelperContract.CODE_DENIED, HelperPolicy.authorize(emptyList(), true).code)
    }

    @Test fun allowlistedPackageIsAccepted() {
        assertEquals(HelperContract.CODE_OK, HelperPolicy.authorize(listOf("com.novoagentehb.app"), true).code)
    }

    @Test fun sharedUidWithOneUnknownPackageStillRequiresAllowlistedName() {
        assertEquals(HelperContract.CODE_OK, HelperPolicy.authorize(listOf("com.other", "com.novoagentehb.app"), true).code)
    }

    @Test fun payloadAndFileLimitsAreExact() {
        assertEquals(HelperContract.CODE_OK, HelperPolicy.checkPayload(HelperContract.MAX_PAYLOAD_BYTES))
        assertEquals(HelperContract.CODE_TOO_LARGE, HelperPolicy.checkPayload(HelperContract.MAX_PAYLOAD_BYTES + 1))
        assertEquals(HelperContract.CODE_TOO_LARGE, HelperPolicy.checkFile(HelperContract.MAX_FILE_BYTES + 1L))
    }

    @Test fun quotaCountsReplacementsNotOnlyAdditions() {
        val full = HelperContract.MAX_TREE_BYTES.toLong()
        // Replacing an existing file of the same size must stay within quota.
        assertEquals(HelperContract.CODE_OK, HelperPolicy.checkQuota(full, 1, 100, 100, false))
        // Adding one more byte beyond quota must fail.
        assertEquals(HelperContract.CODE_QUOTA, HelperPolicy.checkQuota(full, 1, 0, 1, true))
    }

    @Test fun entryCountLimitIsEnforced() {
        assertEquals(HelperContract.CODE_QUOTA, HelperPolicy.checkQuota(0, HelperContract.MAX_TREE_ENTRIES, 0, 1, true))
    }
}
