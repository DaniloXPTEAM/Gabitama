package com.novoagentehb.app.agent.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlPolicyUnitTest {

    private fun allowed(raw: String) = BrowserUrlPolicy.check(raw) is UrlCheck.Allowed
    private fun rejected(raw: String) = BrowserUrlPolicy.check(raw) is UrlCheck.Rejected

    @Test
    fun publicHttpAndHttpsAreAllowed() {
        assertTrue(allowed("https://example.com/path?q=1"))
        assertTrue(allowed("http://example.org"))
        assertTrue(allowed("HTTPS://Example.com"))
    }

    @Test
    fun dangerousSchemesAreRejected() {
        assertTrue(rejected("file:///sdcard/a.txt"))
        assertTrue(rejected("javascript:alert(1)"))
        assertTrue(rejected("intent://x#Intent;end"))
        assertTrue(rejected("content://com.example/x"))
        assertTrue(rejected("example.com"))
    }

    @Test
    fun credentialsAndEmptyAreRejected() {
        assertTrue(rejected("https://user:pass@example.com"))
        assertTrue(rejected("   "))
        assertTrue(rejected("https://" + "a".repeat(BrowserUrlPolicy.MAX_URL_LENGTH)))
    }

    @Test
    fun localAndPrivateHostsAreRejected() {
        assertTrue(rejected("http://localhost:8080"))
        assertTrue(rejected("http://127.0.0.1/"))
        assertTrue(rejected("http://192.168.0.1/"))
        assertTrue(rejected("http://10.1.2.3/"))
        assertTrue(rejected("http://172.16.5.5/"))
        assertTrue(rejected("http://169.254.169.254/latest"))
        assertTrue(rejected("http://[::1]/"))
        assertTrue(rejected("http://[fd00::1]/"))
    }

    @Test
    fun publicLookingIpsAndNamesStartingWithFcAreAllowed() {
        assertTrue(allowed("http://172.32.0.1/"))
        assertTrue(allowed("https://fcbarcelona.com"))
        assertTrue(allowed("https://fdic.gov"))
    }

    @Test
    fun hostCheckOnlyLooksAtTextNotDns() {
        assertFalse(BrowserUrlPolicy.isLocalOrPrivateHost("example.com"))
        assertEquals(false, BrowserUrlPolicy.isLocalOrPrivateHost("8.8.8.8"))
    }
}
