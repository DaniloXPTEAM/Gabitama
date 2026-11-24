package com.novoagentehb.computerhelper

import android.content.Context
import com.novoagentehb.computerhelper.contract.HelperContract
import com.novoagentehb.rootfs.RootfsVerifier
import com.novoagentehb.rootfs.TarRootfsExtractor
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream

/**
 * Provisions the pinned Alpine minirootfs into THIS helper's private filesDir (not GabiTama's).
 *
 * Lifecycle: start() returns at once and runs a single background job; status() reports the state;
 * cancel() asks the job to stop at the next read. Steps: verify the asset SHA-256, extract into a
 * staging directory with the shared hardened extractor, verify the tree, write a marker, then
 * activate with an atomic rename. A valid installation is never replaced; a partial one never
 * becomes active. Any failure or cancel deletes the staging directory.
 */
class RootfsProvisioner(private val context: Context) {

    enum class State { NOT_STARTED, RUNNING, INSTALLED, CANCELLED, FAILED, NO_ASSET }

    data class Snapshot(val state: State, val detail: String = "") {
        fun render(): String = if (detail.isEmpty()) "state=${state.name}" else "state=${state.name} $detail"
    }

    private val lock = Any()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "helper-provision").apply { isDaemon = true }
    }
    @Volatile private var cancelRequested = false
    private var current = Snapshot(State.NOT_STARTED)

    private val filesDir: File get() = context.filesDir
    private val root: File get() = File(filesDir, HelperContract.ROOTFS_DIR)

    /** Starts the job unless one is running or a valid installation exists. Idempotent. */
    fun start(): Snapshot = synchronized(lock) {
        if (current.state == State.RUNNING) return current
        validInstall()?.let { summary ->
            current = Snapshot(State.INSTALLED, render(summary))
            return current
        }
        if (!assetPresent()) {
            current = Snapshot(State.NO_ASSET, "asset=absent")
            return current
        }
        if (filesDir.usableSpace < HelperContract.ROOTFS_MIN_FREE_BYTES) {
            current = Snapshot(State.FAILED, "reason=no-space")
            return current
        }
        cancelRequested = false
        current = Snapshot(State.RUNNING)
        worker.execute { runJob() }
        current
    }

    /** Reports the disk truth first, so an installation survives helper restarts. */
    fun status(): Snapshot = synchronized(lock) {
        if (current.state == State.RUNNING) return current
        validInstall()?.let { summary ->
            current = Snapshot(State.INSTALLED, render(summary))
            return current
        }
        if (current.state == State.INSTALLED) current = Snapshot(State.NOT_STARTED)
        current
    }

    /** Requests cancellation of a running job. The final state is reported by status(). */
    fun cancel(): Snapshot = synchronized(lock) {
        if (current.state == State.RUNNING) cancelRequested = true
        current
    }

    private fun runJob() {
        val staging = File(filesDir, STAGING_PREFIX + UUID.randomUUID())
        val outcome = try {
            cleanupStale()
            checkAssetDigest()
            checkCancelled()
            staging.mkdirs()
            openAsset().use { raw ->
                GZIPInputStream(CancelAwareStream(raw)).use { gz -> TarRootfsExtractor.extract(gz, staging) }
            }
            checkCancelled()
            val summary = RootfsVerifier.verify(
                staging, HelperContract.ALPINE_VERSION, HelperContract.ROOTFS_MAX_ENTRIES, HelperContract.ROOTFS_MAX_BYTES,
            )
            File(staging, MARKER).writeText(
                "version=${HelperContract.ALPINE_VERSION}\nsha256=${HelperContract.ALPINE_SHA256}\n" +
                    "files=${summary.files}\nbytes=${summary.bytes}\n",
            )
            checkCancelled()
            activate(staging)
            Snapshot(State.INSTALLED, render(summary))
        } catch (cancelled: ProvisionCancelled) {
            Snapshot(State.CANCELLED)
        } catch (failure: Exception) {
            Snapshot(State.FAILED, "reason=${reasonOf(failure)}")
        } finally {
            runCatching { deleteTree(staging) }
        }
        synchronized(lock) { current = outcome }
    }

    private fun reasonOf(failure: Exception): String = when (failure) {
        is ProvisionFailed -> failure.message ?: "failed"
        is IllegalStateException -> failure.message ?: "invalid-rootfs"
        is IllegalArgumentException -> "invalid-archive"
        is IOException -> "io"
        else -> "unexpected"
    }

    /** Hashes the asset first; extraction only starts if the pinned digest matches. */
    private fun checkAssetDigest() {
        val digest = MessageDigest.getInstance("SHA-256")
        openAsset().use { raw ->
            val buffer = ByteArray(64 * 1024)
            val stream = CancelAwareStream(raw)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != HelperContract.ALPINE_SHA256) throw ProvisionFailed("sha256-mismatch")
    }

    /** Replaces the rootfs only if it is absent or invalid; never replaces a valid installation. */
    private fun activate(staging: File) {
        if (validInstall() != null) throw ProvisionFailed("already-installed")
        if (root.exists() || Files.isSymbolicLink(root.toPath())) {
            val trash = File(filesDir, TRASH_PREFIX + UUID.randomUUID())
            Files.move(root.toPath(), trash.toPath(), StandardCopyOption.ATOMIC_MOVE)
            deleteTree(trash)
        }
        Files.move(staging.toPath(), root.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    /** Removes staging/trash leftovers from interrupted jobs, and an invalid installation before retry. */
    private fun cleanupStale() {
        filesDir.listFiles()?.forEach { child ->
            if (child.name.startsWith(STAGING_PREFIX) || child.name.startsWith(TRASH_PREFIX)) deleteTree(child)
        }
        if (root.exists() || Files.isSymbolicLink(root.toPath())) {
            val trash = File(filesDir, TRASH_PREFIX + UUID.randomUUID())
            Files.move(root.toPath(), trash.toPath(), StandardCopyOption.ATOMIC_MOVE)
            deleteTree(trash)
        }
    }

    private fun validInstall(): RootfsVerifier.Summary? {
        if (!Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        val marker = File(root, MARKER)
        if (!marker.isFile) return null
        val text = runCatching { marker.readText() }.getOrNull() ?: return null
        if (!text.contains("version=${HelperContract.ALPINE_VERSION}\n") ||
            !text.contains("sha256=${HelperContract.ALPINE_SHA256}\n")
        ) return null
        return try {
            RootfsVerifier.verify(root, HelperContract.ALPINE_VERSION, HelperContract.ROOTFS_MAX_ENTRIES, HelperContract.ROOTFS_MAX_BYTES)
        } catch (invalid: Exception) {
            null
        }
    }

    private fun assetPresent(): Boolean =
        runCatching { context.assets.open(HelperContract.ALPINE_ASSET).close() }.isSuccess

    private fun openAsset(): InputStream = context.assets.open(HelperContract.ALPINE_ASSET)

    private fun checkCancelled() {
        if (cancelRequested) throw ProvisionCancelled()
    }

    private fun render(summary: RootfsVerifier.Summary): String =
        "release=${summary.release} arch=${summary.arch} files=${summary.files} bytes=${summary.bytes} " +
            "sha256=${HelperContract.ALPINE_SHA256}"

    /** Deletes a tree without following links: a symlink is removed itself, never its target. */
    private fun deleteTree(target: File) {
        val path = target.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /** Checks the cancel flag on every read so a long extraction stops promptly. */
    private inner class CancelAwareStream(inner: InputStream) : FilterInputStream(inner) {
        override fun read(): Int {
            checkCancelled()
            return super.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            checkCancelled()
            return super.read(b, off, len)
        }
    }

    private class ProvisionCancelled : RuntimeException("cancelled")

    private class ProvisionFailed(reason: String) : RuntimeException(reason)

    companion object {
        private const val STAGING_PREFIX = "alpine-staging-"
        private const val TRASH_PREFIX = "alpine-trash-"
        private const val MARKER = ".gabitama-provision"
    }
}
