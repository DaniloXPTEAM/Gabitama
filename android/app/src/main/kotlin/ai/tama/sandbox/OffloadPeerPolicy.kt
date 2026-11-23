package ai.tama.sandbox

/** Admission at the host socket; a session string supplied by the guest is not identity. */
internal object OffloadPeerPolicy {
    data class Decision(val allowed: Boolean, val sessionId: String?, val refusal: String? = null)

    fun resolve(
        parallel: Boolean, peer: OffloadPeerLease?, guestSession: String?, name: String, argv: List<String>,
    ): Decision {
        if (!parallel) return Decision(true, guestSession)
        if (peer != null) {
            return if (peer.isCurrent()) Decision(true, peer.sessionId)
            else Decision(false, null, "native_offload: shell session has stopped\n")
        }
        // No request from an unregistered process is trusted, including
        // non-media commands. PTY peers need explicit host-side registration.
        return Decision(false, null, "native_offload: unrecognized shell session\n")
    }
}
