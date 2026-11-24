package com.novoagentehb.computerhelper

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.novoagentehb.computerhelper.contract.HelperContract
import com.novoagentehb.computerhelper.contract.HelperPolicy
import com.novoagentehb.computerhelper.contract.TreePath
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Experimental helper. Serves fixed operations on its OWN private tree under filesDir.
 * Every transaction re-checks the caller (permission + allowlisted package of the Binder UID).
 * It never reads GabiTama data except metadata access probes, and never executes anything.
 */
class HelperService : Service() {
    private val ops = Semaphore(HelperContract.MAX_CONCURRENT_OPS)

    private val binder = object : Binder() {
        init { attachInterface(null, HelperContract.DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) { reply?.writeString(HelperContract.DESCRIPTOR); return true }
            data.enforceInterface(HelperContract.DESCRIPTOR)
            if (reply == null) return false
            val decision = authorizeCaller()
            if (decision.code != HelperContract.CODE_OK) {
                writeReply(reply, OpResult(decision.code, ""))
                return true
            }
            if (!ops.tryAcquire(HelperContract.OP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                writeReply(reply, OpResult(HelperContract.CODE_BUSY, ""))
                return true
            }
            try {
                val started = System.nanoTime()
                val result = try {
                    dispatch(code, data)
                } catch (failure: Exception) {
                    Log.w(TAG, "operation failed: ${failure.javaClass.simpleName}")
                    OpResult(HelperContract.CODE_IO, "")
                }
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                writeReply(reply, if (elapsedMs > HelperContract.OP_TIMEOUT_MS) OpResult(HelperContract.CODE_TIMEOUT, "") else result)
            } finally {
                ops.release()
            }
            return true
        }
    }

    /** Outcome of one operation: result code, short text, and optional bytes. */
    private data class OpResult(val code: Int, val text: String, val bytes: ByteArray = ByteArray(0))

    /** Fixed reply layout for every transaction: exception header, code, text, bytes. */
    private fun writeReply(reply: Parcel, result: OpResult) {
        reply.writeNoException()
        reply.writeInt(result.code)
        reply.writeString(result.text)
        reply.writeByteArray(result.bytes)
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Inside onBind, Binder.getCallingUid() is the system server, not the client, so the caller
        // cannot be authorized here. Bind is gated by the service's android:permission (enforced by the
        // system), and every transaction below re-checks the real calling UID before doing anything.
        return binder
    }

    private fun authorizeCaller(): HelperPolicy.Decision {
        val uid = Binder.getCallingUid()
        val granted = checkCallingPermission(HelperContract.PERMISSION) == PackageManager.PERMISSION_GRANTED
        val packages = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        return HelperPolicy.authorize(packages, granted)
    }

    private fun dispatch(code: Int, data: Parcel): OpResult = when (code) {
        HelperContract.TX_STATUS -> OpResult(
            HelperContract.CODE_OK,
            "uid=${Process.myUid()} pid=${Process.myPid()} internet=${checkSelfPermission("android.permission.INTERNET") == PackageManager.PERMISSION_GRANTED}",
        )
        HelperContract.TX_WRITE -> {
            val rel = data.readString().orEmpty()
            val payload = data.createByteArray() ?: ByteArray(0)
            write(rel, payload)
        }
        HelperContract.TX_READ -> read(data.readString().orEmpty())
        HelperContract.TX_SHA256 -> read(data.readString().orEmpty()).let { r ->
            if (r.code != HelperContract.CODE_OK) r
            else OpResult(HelperContract.CODE_OK, sha256(r.bytes))
        }
        HelperContract.TX_ACCESS_PROBE -> accessProbe(data.readString().orEmpty())
        HelperContract.TX_PROVISION_START -> provision(provisioner.start())
        HelperContract.TX_PROVISION_STATUS -> provision(provisioner.status())
        HelperContract.TX_PROVISION_CANCEL -> provision(provisioner.cancel())
        HelperContract.TX_PROOT_START -> {
            val (code, snapshot) = prootRunner.start(data.readInt())
            OpResult(code, snapshot.render())
        }
        HelperContract.TX_TEST_X86_PROVISION_START -> provision(x86Provisioner.start())
        HelperContract.TX_TEST_X86_PROVISION_STATUS -> provision(x86Provisioner.status())
        HelperContract.TX_TEST_X86_PROVISION_CANCEL -> provision(x86Provisioner.cancel())
        HelperContract.TX_PROOT_STATUS -> OpResult(HelperContract.CODE_OK, prootRunner.status().render())
        HelperContract.TX_PROOT_CANCEL -> OpResult(HelperContract.CODE_OK, prootRunner.cancel().render())
        else -> OpResult(HelperContract.CODE_BAD_REQUEST, "")
    }

    private val provisioner by lazy { RootfsProvisioner(this) }

    /** Test-only Alpine x86_64 variant; kept apart from [provisioner] (aarch64 product path). */
    private val x86Provisioner by lazy { RootfsProvisioner(this, RootfsProvisioner.Variant.X86_64_TEST) }

    private val prootRunner by lazy { ProotRunner(this, x86Provisioner) }

    private fun provision(snapshot: RootfsProvisioner.Snapshot): OpResult = OpResult(
        when (snapshot.state) {
            RootfsProvisioner.State.FAILED -> HelperContract.CODE_IO
            RootfsProvisioner.State.NO_ASSET -> HelperContract.CODE_NOT_INSTALLED
            else -> HelperContract.CODE_OK
        },
        snapshot.render(),
    )

    private fun root(): File = File(filesDir, TREE_DIR).apply { mkdirs() }

    /** Resolves a validated relative path inside the tree, refusing symlinked ancestors and escapes. */
    private fun resolve(rel: String): File? {
        if (TreePath.validate(rel) != HelperContract.CODE_OK) return null
        val rootDir = root()
        val rootCanonical = rootDir.canonicalPath
        var current = rootDir
        for (segment in rel.split('/')) {
            current = File(current, segment)
            if (java.nio.file.Files.isSymbolicLink(current.toPath())) return null
        }
        val canonical = current.canonicalPath
        if (!canonical.startsWith("$rootCanonical/")) return null
        return current
    }

    private fun write(rel: String, payload: ByteArray): OpResult {
        val payloadCheck = HelperPolicy.checkPayload(payload.size)
        if (payloadCheck != HelperContract.CODE_OK) return OpResult(payloadCheck, "")
        val fileCheck = HelperPolicy.checkFile(payload.size.toLong())
        if (fileCheck != HelperContract.CODE_OK) return OpResult(fileCheck, "")
        val target = resolve(rel) ?: return OpResult(HelperContract.CODE_BAD_PATH, "")
        val (existingBytes, existingEntries) = treeUsage()
        val replaced = if (target.isFile) target.length() else 0L
        val quota = HelperPolicy.checkQuota(existingBytes, existingEntries, replaced, payload.size.toLong(), !target.exists())
        if (quota != HelperContract.CODE_OK) return OpResult(quota, "")
        val parent = target.parentFile ?: return OpResult(HelperContract.CODE_BAD_PATH, "")
        if (!parent.exists()) {
            // Only directories inside the tree; resolve() already ensured no escape.
            if (!parent.mkdirs()) return OpResult(HelperContract.CODE_IO, "")
        }
        if (target.exists() && !target.isFile) return OpResult(HelperContract.CODE_BAD_PATH, "")
        // Atomic replace: write a sibling temp file, fsync, then rename within the same directory.
        val temp = File(parent, ".tmp-" + UUID.randomUUID())
        temp.outputStream().use { out -> out.write(payload); out.flush(); out.fd.sync() }
        try {
            Os.rename(temp.absolutePath, target.absolutePath)
        } catch (failure: Exception) {
            temp.delete()
            return OpResult(HelperContract.CODE_IO, "")
        }
        return OpResult(HelperContract.CODE_OK, "bytes=${payload.size}")
    }

    private fun read(rel: String): OpResult {
        val target = resolve(rel) ?: return OpResult(HelperContract.CODE_BAD_PATH, "")
        if (!target.isFile) return OpResult(HelperContract.CODE_NOT_FOUND, "")
        val size = target.length()
        val check = HelperPolicy.checkFile(size)
        if (check != HelperContract.CODE_OK) return OpResult(check, "")
        return OpResult(HelperContract.CODE_OK, "bytes=$size", target.readBytes())
    }

    private fun treeUsage(): Pair<Long, Int> {
        var bytes = 0L
        var entries = 0
        root().walkTopDown().forEach { f ->
            if (f.isFile) { bytes += f.length(); entries++ }
        }
        return bytes to entries
    }

    /**
     * Metadata-only probe of the CALLER's private directories. Reports errno names for existence and
     * read access. Never opens files and never returns content. The caller package must be the
     * calling UID's own package and allowlisted.
     */
    private fun accessProbe(callerPackage: String): OpResult {
        val uid = Binder.getCallingUid()
        val packages = packageManager.getPackagesForUid(uid)?.toList().orEmpty()
        if (callerPackage !in packages || callerPackage !in HelperContract.ALLOWED_CLIENT_PACKAGES) {
            return OpResult(HelperContract.CODE_DENIED, "")
        }
        val base = "/data/user/0/$callerPackage"
        val parts = HelperContract.PROBE_TARGETS.map { target ->
            val path = "$base/$target"
            val result = try {
                Os.access(path, OsConstants.R_OK)
                "readable"
            } catch (failure: android.system.ErrnoException) {
                failure.errno.let { errnoName(it) }
            }
            "$target=$result"
        }
        return OpResult(HelperContract.CODE_OK, parts.joinToString(" "))
    }

    private fun errnoName(errno: Int): String = when (errno) {
        OsConstants.EACCES -> "EACCES"
        OsConstants.ENOENT -> "ENOENT"
        OsConstants.EPERM -> "EPERM"
        else -> "errno$errno"
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "ComputerHelper"
        private const val TREE_DIR = "computer-poc-tree"
    }
}
