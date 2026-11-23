package com.novoagentehb.app.computer.runtime

import android.os.ParcelFileDescriptor
import android.system.Os
import java.io.File

/**
 * Privileged-side broker: opens ONE regular file granted by [RootfsGrantPolicy] and returns a
 * read-only descriptor. The descriptor is verified through `/proc/self/fd/<n>`: its real path
 * must equal the canonical path that was validated, so a file or directory swapped for a link
 * between validation and open is refused. Only the descriptor is ever sent to an isolated
 * process; no path, directory handle or command is handed over.
 *
 * Not wired to any UI or service yet. Test coverage: RootfsGrantAndroidTest in the isolation fixture.
 */
object RootfsGrantBroker {
    fun openRegularFile(root: File, relative: String): ParcelFileDescriptor {
        val file = when (val decision = RootfsGrantPolicy.resolveRegularFile(root, relative)) {
            is RootfsGrantPolicy.Decision.Granted -> decision.file
            is RootfsGrantPolicy.Decision.Rejected ->
                throw SecurityException("Concessão recusada: ${decision.reason}")
        }
        val expected = file.canonicalPath
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val opened = Os.readlink("/proc/self/fd/${descriptor.fd}")
            if (opened != expected) throw SecurityException("Concessão recusada: objeto aberto difere do validado")
            return descriptor
        } catch (failure: Throwable) {
            descriptor.close()
            throw failure
        }
    }
}
