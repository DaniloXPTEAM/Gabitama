package com.openminis.app.sandbox

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Protects host writes from a pre-existing final-component symlink.
 * This does not protect a directory writable by a process with the same UID. */
internal object OffloadReplyFiles {
    fun writeNew(dir: File, prefix: String, output: String): File {
        val file = File(dir, prefix + UUID.randomUUID().toString())
        writeExclusive(file, output)
        return file
    }

    internal fun writeExclusive(file: File, output: String) {
        val flags = OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or
            OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC
        val fd = Os.open(file.absolutePath, flags, 0x180) // 0600
        FileOutputStream(fd).use { it.write(output.toByteArray(Charsets.UTF_8)) }
    }
}
