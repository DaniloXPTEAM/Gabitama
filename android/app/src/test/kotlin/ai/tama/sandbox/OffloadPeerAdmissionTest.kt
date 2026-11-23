package ai.tama.sandbox

import ai.tama.net.NativeOffloadSession
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class OffloadPeerAdmissionTest {
    @Test fun validatesSessionNamesBeforeAnyDirectoryUse() {
        for (id in listOf("", ".", "..", "../secret", "a/b", "a\\b", "a\n", "a".repeat(256)))
            assertFalse(id, NativeOffloadSession.valid(true, id))
        assertTrue(NativeOffloadSession.valid(true, "session-123"))
    }

    @Test fun hostPeerIdentityWinsAndRevokedPeerCannotAuthorize() {
        val lease = OffloadPeerBindings.register(91033, "session-a") { true }
        try {
            val admitted = OffloadPeerPolicy.resolve(true, lease, "forged", "nanomuse-media", listOf("nanomuse-media", "image"))
            assertTrue(admitted.allowed)
            assertEquals("session-a", admitted.sessionId)
            assertFalse(OffloadPeerPolicy.resolve(true, null, "session-a", "android-open", emptyList()).allowed)
            assertFalse(OffloadPeerPolicy.resolve(true, null, null, "android-open", emptyList()).allowed)
            assertFalse(OffloadPeerPolicy.resolve(true, null, null, "nanomuse-media", listOf("nanomuse-media", "status")).allowed)
            assertFalse(OffloadPeerPolicy.resolve(true, null, null, "nanomuse-media", listOf("nanomuse-media", "image")).allowed)
            OffloadPeerBindings.release(91033, lease)
            assertFalse(OffloadPeerPolicy.resolve(true, lease, null, "android-open", emptyList()).allowed)
        } finally { OffloadPeerBindings.release(91033, lease) }
    }

    @Test fun launcherParsesOnlyPidAndDoesNotConsumeShellOutput() {
        val input = ByteArrayInputStream("1234\nNEXT".toByteArray())
        assertEquals(1234, OffloadPeerLaunch.readPid(input))
        assertEquals("NEXT", input.readBytes().toString(Charsets.UTF_8))
        for (bad in listOf("0\n", "123x\n", "123", "12345678901\n")) {
            try { OffloadPeerLaunch.readPid(ByteArrayInputStream(bad.toByteArray())); fail("accepted bad PID") }
            catch (_: IOException) { }
        }
    }
}
