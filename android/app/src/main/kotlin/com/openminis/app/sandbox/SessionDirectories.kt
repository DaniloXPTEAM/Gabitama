package com.openminis.app.sandbox

import ai.tama.net.NativeOffloadSession
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

/** Reject existing links at every component. This is not a same-UID race boundary. */
internal object SessionDirectories {
    fun ensure(filesDir: File, sessionId: String) {
        require(NativeOffloadSession.valid(true, sessionId)) { "Invalid session ID" }
        val base = directory(filesDir.toPath(), "minis-sessions")
        val session = directory(base, sessionId)
        for (name in listOf("attachments", "offloads", "workspace", "browser")) {
            directory(session, name)
        }
    }

    private fun directory(parent: Path, name: String): Path {
        val path = parent.resolve(name)
        try {
            Files.createDirectory(path)
        } catch (_: FileAlreadyExistsException) {
            // Existing real directories are allowed; links and files are not.
        }
        require(Files.isDirectory(path, NOFOLLOW_LINKS)) { "Unsafe session directory: $name" }
        return path
    }
}
