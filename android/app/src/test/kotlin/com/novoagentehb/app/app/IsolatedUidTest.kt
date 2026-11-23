package com.novoagentehb.app.app

import org.junit.Assert.*
import org.junit.Test

class IsolatedUidTest {
    @Test fun handlesRegularAndIsolatedUidsAcrossAndroidUsers() {
        for (uid in listOf(-1, 0, 1000, 10000, 19999, 110000, 119999))
            assertFalse("uid $uid", IsolatedUid.isIsolated(uid))
        for (uid in listOf(90000, 98999, 99000, 99999, 190000, 199999))
            assertTrue("uid $uid", IsolatedUid.isIsolated(uid))
    }
}
