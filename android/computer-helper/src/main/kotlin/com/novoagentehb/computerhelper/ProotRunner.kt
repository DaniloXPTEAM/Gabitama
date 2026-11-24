package com.novoagentehb.computerhelper

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.novoagentehb.computerhelper.contract.HelperContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Runs the ORIGINAL PRoot binary (`nativeLibraryDir/libproot.so`) for FIXED operations only.
 *
 * - The caller sends an operation id, never argv, paths or environment.
 * - One run at a time; each run has a hard deadline and its output is capped.
 * - Cancel and timeout kill the whole process tree (PRoot and its tracees), then report leftovers.
 *
 * Passing these operations shows that the binary loads and that its lifecycle is controlled on this
 * device. It does not prove an Alpine rootfs, a PTY, a persistent shell or isolation from the caller.
 */
class ProotRunner(private val context: Context) {

    enum class State { IDLE, RUNNING, EXITED, TIMEOUT, CANCELLED, FAILED }

    data class Snapshot(
        val state: State,
        val op: Int,
        val exitCode: Int,
        val elapsedMs: Long,
        val note: String,
        val output: String,
    ) {
        fun render(): String =
            "state=${state.name} op=$op exit=$exitCode elapsedMs=$elapsedMs note=$note output=${output.replace("\n", " | ")}"
    }

    private class Run(val op: Int) {
        val startedAt: Long = System.nanoTime()
        @Volatile var pid: Int = -1
        @Volatile var process: Process? = null
        @Volatile var cancelRequested: Boolean = false
        @Volatile var terminal: Snapshot? = null
    }

    private val lock = Any()
    @Volatile private var run: Run? = null

    /** Returns the result code and the snapshot right after the start attempt. */
    fun start(op: Int): Pair<Int, Snapshot> = synchronized(lock) {
        run?.takeIf { it.terminal == null }?.let { return Pair(HelperContract.CODE_BUSY, snapshotOf(it)) }
        val binary = File(context.applicationInfo.nativeLibraryDir, BINARY)
        val argv = argvFor(op, binary.absolutePath)
            ?: return Pair(HelperContract.CODE_BAD_REQUEST, plain(State.FAILED, op, "bad-op"))
        if (!binary.isFile || !binary.canExecute()) {
            return Pair(HelperContract.CODE_NOT_INSTALLED, plain(State.FAILED, op, "binary-missing"))
        }
        val builder = ProcessBuilder(argv).redirectErrorStream(true)
        val env = builder.environment()
        env.clear()
        env["PATH"] = "/system/bin"
        val loader = File(context.applicationInfo.nativeLibraryDir, LOADER)
        if (loader.isFile) env["PROOT_LOADER"] = loader.absolutePath
        val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        env["PROOT_TMP_DIR"] = tmp.absolutePath

        val process = try {
            builder.start()
        } catch (failure: IOException) {
            return Pair(HelperContract.CODE_IO, plain(State.FAILED, op, "spawn-failed"))
        }
        val current = Run(op)
        current.process = process
        current.pid = pidOf(process)
        run = current

        val output = ByteArrayOutputStream()
        val reader = Thread({ copyCapped(process.inputStream, output) }, "proot-reader").apply {
            isDaemon = true
            start()
        }
        Thread({ supervise(current, process, reader, output) }, "proot-supervisor").apply {
            isDaemon = true
            start()
        }
        Pair(HelperContract.CODE_OK, snapshotOf(current))
    }

    fun status(): Snapshot = run?.let { snapshotOf(it) } ?: plain(State.IDLE, 0, "none")

    /** Requests cancellation of the active run and kills its process tree. Idempotent. */
    fun cancel(): Snapshot {
        val current = run ?: return plain(State.IDLE, 0, "none")
        if (current.terminal == null && !current.cancelRequested) {
            current.cancelRequested = true
            killTree(current.pid, current.process)
        }
        return snapshotOf(current)
    }

    private fun supervise(current: Run, process: Process, reader: Thread, output: ByteArrayOutputStream) {
        val deadline = deadlineFor(current.op)
        val finished = process.waitFor(deadline, TimeUnit.MILLISECONDS)
        var state: State
        var exit = -1
        var note = "pid=${current.pid}"
        if (current.cancelRequested) {
            killTree(current.pid, process)
            process.waitFor(CLEANUP_WAIT_MS, TimeUnit.MILLISECONDS)
            state = State.CANCELLED
        } else if (!finished) {
            killTree(current.pid, process)
            process.waitFor(CLEANUP_WAIT_MS, TimeUnit.MILLISECONDS)
            state = State.TIMEOUT
            note += " deadlineMs=$deadline"
        } else {
            exit = process.exitValue()
            state = State.EXITED
        }
        reader.join(READER_WAIT_MS)
        val text = synchronized(output) { String(output.toByteArray(), Charsets.UTF_8) }
        if (current.op == HelperContract.PROOT_OP_HOST_SLEEP) {
            note += " leftover=${countSleepSurvivors()}"
        }
        current.terminal = Snapshot(
            state = state,
            op = current.op,
            exitCode = exit,
            elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current.startedAt),
            note = note,
            output = text,
        )
    }

    private fun snapshotOf(current: Run): Snapshot = current.terminal
        ?: Snapshot(
            state = State.RUNNING,
            op = current.op,
            exitCode = -1,
            elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current.startedAt),
            note = "pid=${current.pid}",
            output = "",
        )

    private fun plain(state: State, op: Int, note: String) = Snapshot(state, op, -1, 0, note, "")

    private fun argvFor(op: Int, binary: String): List<String>? = when (op) {
        HelperContract.PROOT_OP_VERSION -> listOf(binary, "--version")
        // Fixed host-root sleep: used only to exercise timeout and cancellation of a live tracee.
        HelperContract.PROOT_OP_HOST_SLEEP -> listOf(binary, "--kill-on-exit", "-r", "/", SLEEP_BIN, "60")
        else -> null
    }

    private fun deadlineFor(op: Int): Long = when (op) {
        HelperContract.PROOT_OP_HOST_SLEEP -> HelperContract.PROOT_SLEEP_DEADLINE_MS
        else -> HelperContract.PROOT_VERSION_DEADLINE_MS
    }

    private fun copyCapped(input: InputStream, output: ByteArrayOutputStream) {
        input.use { stream ->
            val buffer = ByteArray(512)
            while (true) {
                val read = try { stream.read(buffer) } catch (ignored: IOException) { -1 }
                if (read < 0) break
                synchronized(output) {
                    val room = HelperContract.PROOT_MAX_OUTPUT_BYTES - output.size()
                    if (room > 0) output.write(buffer, 0, minOf(room, read))
                }
            }
        }
    }

    /** Kills descendants first (tracees whose parent is PRoot), then the root. */
    private fun killTree(pid: Int, process: Process?) {
        if (pid > 0) {
            for (child in descendantsOf(pid)) {
                runCatching { Os.kill(child, OsConstants.SIGKILL) }
            }
            runCatching { Os.kill(pid, OsConstants.SIGKILL) }
        }
        process?.destroyForcibly()
    }

    private fun descendantsOf(root: Int): List<Int> {
        val parents = HashMap<Int, MutableList<Int>>()
        for ((pid, ppid) in processTable()) parents.getOrPut(ppid) { mutableListOf() }.add(pid)
        val out = mutableListOf<Int>()
        val queue = ArrayDeque<Int>().apply { add(root) }
        while (queue.isNotEmpty()) {
            for (child in parents[queue.removeFirst()].orEmpty()) {
                out.add(child)
                queue.add(child)
            }
        }
        return out
    }

    /** Pid to parent-pid map from /proc/<pid>/stat, for processes readable by this UID. */
    private fun processTable(): Map<Int, Int> {
        val table = HashMap<Int, Int>()
        File("/proc").listFiles()?.forEach { entry ->
            val pid = entry.name.toIntOrNull() ?: return@forEach
            val stat = runCatching { File(entry, "stat").readText() }.getOrNull() ?: return@forEach
            val fields = stat.substring(stat.lastIndexOf(')') + 2).split(' ')
            fields.getOrNull(1)?.toIntOrNull()?.let { table[pid] = it }
        }
        return table
    }

    /** Live processes of this UID still running the fixed sleep binary (cmdline match). */
    private fun countSleepSurvivors(): Int {
        val myUid = android.os.Process.myUid()
        var count = 0
        File("/proc").listFiles()?.forEach { entry ->
            if (entry.name.toIntOrNull() == null) return@forEach
            val cmdline = runCatching { File(entry, "cmdline").readBytes() }.getOrNull() ?: return@forEach
            if (!String(cmdline, Charsets.UTF_8).startsWith(SLEEP_BIN)) return@forEach
            val uid = runCatching {
                File(entry, "status").readLines().first { it.startsWith("Uid:") }
                    .split(Regex("\\s+"))[1].toInt()
            }.getOrNull()
            if (uid == myUid) count++
        }
        return count
    }

    private fun pidOf(process: Process): Int = try {
        val field = process.javaClass.getDeclaredField("pid")
        field.isAccessible = true
        field.getInt(process)
    } catch (ignored: Exception) {
        -1
    }

    companion object {
        const val BINARY = "libproot.so"
        const val LOADER = "libproot-loader.so"
        private const val SLEEP_BIN = "/system/bin/sleep"
        private const val CLEANUP_WAIT_MS = 2_000L
        private const val READER_WAIT_MS = 2_000L
    }
}
