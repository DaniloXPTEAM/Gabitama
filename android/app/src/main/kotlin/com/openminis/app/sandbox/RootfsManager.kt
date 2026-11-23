package com.openminis.app.sandbox

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * Observable state for rootfs installation. Mirrors the conceptual iOS
 * `downloadRootfs(progress:)` contract: discrete phases with a 0..1 fraction
 * during the long-running extract step. Neither platform actually downloads
 * the rootfs from the network today — it ships bundled as an asset — so
 * "progress" tracks asset-stream consumption (compressed bytes) instead of
 * HTTP transfer. The fraction semantics are identical from the UI's viewpoint.
 */
sealed class RootfsInstallState {
    object Idle : RootfsInstallState()
    object Preparing : RootfsInstallState()
    /** progress in 0f..1f, based on compressed asset bytes consumed. */
    data class Extracting(val progress: Float) : RootfsInstallState()
    object Finalizing : RootfsInstallState()
    object Installed : RootfsInstallState()
    data class Failed(val error: String) : RootfsInstallState()
}

/**
 * Manages Alpine Linux rootfs installation and PRoot binary extraction.
 * Corresponds to iOS RootfsManager.swift.
 */
class RootfsManager private constructor(private val context: Context) {

    val rootfsDir: File = File(context.filesDir, "alpine-rootfs")
    val prootBinary: File = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    private val archFile: File get() = File(rootfsDir, ".arch")

    val isInstalled: Boolean
        get() = java.nio.file.Files.isDirectory(rootfsDir.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                java.nio.file.Files.isRegularFile(archFile.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                runCatching { archFile.readText().trim() == ARCH }.getOrDefault(false)

    /**
     * Observable install progress. UI layers (OnboardingScreen,
     * RootfsManagementScreen) bind this and render a progress bar during
     * `installIfNeeded()` / `reset()`. Emits `Installed` on success and
     * `Failed` on error so callers can surface retry affordances.
     */
    private val _installState = MutableStateFlow<RootfsInstallState>(
        if (isInstalled) RootfsInstallState.Installed else RootfsInstallState.Idle
    )
    val installState: StateFlow<RootfsInstallState> = _installState.asStateFlow()

    /**
     * Install Alpine rootfs from assets if not already present.
     * Extracts alpine-minirootfs.tar.gz using manual POSIX tar parsing.
     * Progress is published to [installState] (Preparing → Extracting(f) →
     * Finalizing → Installed / Failed).
     */
    suspend fun installIfNeeded() = withContext(Dispatchers.IO) {
        if (isInstalled) {
            Log.d(TAG, "Rootfs already installed at $rootfsDir")
            _installState.value = RootfsInstallState.Installed
            return@withContext
        }

        var staging: File? = null
        try {
            _installState.value = RootfsInstallState.Preparing
            Log.i(TAG, "Installing Alpine rootfs...")

            // GabiTama: never delete an unrecognized existing installation or its user data.
            check(!java.nio.file.Files.exists(rootfsDir.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                "O rootfs existente não foi reconhecido; ele foi preservado."
            }

            // Extract rootfs from assets.
            // AAPT may decompress .tar.gz → .tar automatically, so try both names.
            val assetName = try {
                context.assets.open(ROOTFS_ASSET).close()
                ROOTFS_ASSET
            } catch (_: java.io.FileNotFoundException) {
                ROOTFS_ASSET_TAR
            }

            // Verify the asset exists before creating a staging directory.
            context.assets.open(assetName).close()
            val installDir = java.nio.file.Files.createTempDirectory(context.filesDir.toPath(), "tama-rootfs-staging-").toFile()
            staging = installDir

            // Asset size for progress calculation — compressed length (for .gz)
            // or uncompressed length (for .tar). openFd() fails for 0-length
            // assets on some devices; fall back to 0 which disables progress.
            val assetTotal: Long = try {
                context.assets.openFd(assetName).use { it.length }
            } catch (_: Exception) { 0L }

            // Emit an initial 0% so the UI flips from Preparing → progress bar.
            _installState.value = RootfsInstallState.Extracting(0f)

            context.assets.open(assetName).use { rawAsset ->
                // Wrap the ASSET stream (not the gzip stream) so progress tracks
                // compressed bytes consumed — monotonic and matches the size we
                // have a total for. Throttle updates to avoid flooding the StateFlow.
                val progressStream = ProgressInputStream(rawAsset, assetTotal) { fraction ->
                    _installState.value = RootfsInstallState.Extracting(fraction)
                }
                if (assetName.endsWith(".gz")) {
                    GZIPInputStream(progressStream).use { gzipStream ->
                        extractTar(gzipStream, installDir)
                    }
                } else {
                    extractTar(progressStream, installDir)
                }
            }

            _installState.value = RootfsInstallState.Finalizing

            // Fail closed if the archive did not produce the expected executable.
            val busybox = File(installDir, "bin/busybox")
            check(busybox.isFile && !java.nio.file.Files.isSymbolicLink(busybox.toPath()) &&
                busybox.canonicalFile.toPath().startsWith(installDir.canonicalFile.toPath())) { "Rootfs incompleto: bin/busybox ausente." }
            // Write arch marker
            File(installDir, ".arch").writeText(ARCH)

            // Pre-create /var/minis directories. Mirrors iOS
            // RootfsManager.swift:76-80 (attachments/offloads/workspace/skills/
            // shared) plus Android-specific `memory` kept from prior parity work.
            // T219-6: also pre-create `mounts/` so PRoot's `-b host:/var/minis/mounts/<name>`
            // has the parent directory to bind into; without this, PRoot silently
            // skips bind mounts whose target path doesn't exist.
            val minisSubdirs = listOf("attachments", "offloads", "workspace", "skills", "memory", "shared", "mounts")
            for (subdir in minisSubdirs) {
                File(installDir, "var/minis/$subdir").mkdirs()
            }

            // Pre-create /opt/bin — appears in PATH so users can drop third-party
            // binaries here without first `mkdir -p`. Matches iOS PATH layout.
            File(installDir, "opt/bin").mkdirs()

            // GabiTama: no replace option; concurrent/existing roots are never overwritten.
            java.nio.file.Files.move(installDir.toPath(), rootfsDir.toPath())
            staging = null

            // Write resolv.conf from system DNS (fallback to 8.8.8.8)
            refreshDns()

            // Flag a fresh install so MirrorSpeedTestViewModel.autoDetectOnceIfNeeded()
            // picks the fastest mirror for each category on first boot.
            context.getSharedPreferences("mirror_settings", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("rootfs.freshInstall", true)
                .apply()

            Log.i(TAG, "Rootfs installation complete")
            _installState.value = RootfsInstallState.Installed
        } catch (t: Throwable) {
            Log.e(TAG, "Rootfs installation failed", t)
            _installState.value = RootfsInstallState.Failed(t.message ?: t.javaClass.simpleName)
            throw t
        } finally {
            staging?.let { dir -> runCatching { deleteStagingWithoutFollowingLinks(dir) } }
        }
    }

    /** Only clean up the staging directory we created; never traverse symbolic links. */
    private fun deleteStagingWithoutFollowingLinks(dir: File) {
        val parent = context.filesDir.canonicalFile.toPath()
        val path = dir.toPath().toAbsolutePath().normalize()
        require(path.parent == parent && path.fileName.toString().startsWith("tama-rootfs-staging-"))
        if (!java.nio.file.Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        java.nio.file.Files.walkFileTree(path, object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                java.nio.file.Files.delete(file)
                return java.nio.file.FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(directory: java.nio.file.Path, exc: java.io.IOException?): java.nio.file.FileVisitResult {
                if (exc != null) throw exc
                java.nio.file.Files.delete(directory)
                return java.nio.file.FileVisitResult.CONTINUE
            }
        })
    }

    /** Directory containing extracted native libraries (read-only, executable). */
    val nativeLibDir: File = File(context.applicationInfo.nativeLibraryDir)

    /**
     * Verify PRoot binary is available in the native library directory.
     */
    suspend fun installProotIfNeeded() = withContext(Dispatchers.IO) {
        if (!prootBinary.exists() || !prootBinary.canExecute()) {
            throw IllegalStateException(
                "PRoot binary not found at $prootBinary. " +
                "It should be auto-extracted from jniLibs."
            )
        }

        // No libtalloc staging: deps/build_proot.sh links talloc statically
        // (the binary carries no DT_NEEDED for libtalloc.so), so there is no
        // shared object to version-rename. Older builds shipped libtalloc.so
        // in jniLibs and copied it here as libtalloc.so.2.

        Log.d(TAG, "PRoot binary available at $prootBinary")
    }

    /**
     * Reset rootfs. Optionally keeps user data (/root).
     * Returns the backup directory if keepUserData is true, null otherwise.
     */
    suspend fun reset(keepUserData: Boolean = false, userConfirmed: Boolean = false): File? = withContext(Dispatchers.IO) {
        require(userConfirmed) { "Reset do rootfs requer confirmação explícita." }
        var backupDir: File? = null

        if (keepUserData) {
            val rootHome = File(rootfsDir, "root")
            if (rootHome.exists()) {
                backupDir = File(context.cacheDir, "rootfs-backup-root")
                backupDir.deleteRecursively()
                rootHome.copyRecursively(backupDir, overwrite = true)
            }
        }

        rootfsDir.deleteRecursively()
        installIfNeeded()

        // Restore user data
        if (backupDir != null && backupDir.exists()) {
            val rootHome = File(rootfsDir, "root")
            backupDir.copyRecursively(rootHome, overwrite = true)
            backupDir.deleteRecursively()
        }

        backupDir
    }

    /**
     * Calculate the total size of the rootfs directory in bytes.
     */
    suspend fun getRootfsSize(): Long = withContext(Dispatchers.IO) {
        RootfsSize.measure(rootfsDir)
    }

    /**
     * Restore user data from a backup directory into /root.
     */
    suspend fun restoreUserData(backupDir: File, userConfirmed: Boolean = false) = withContext(Dispatchers.IO) {
        require(userConfirmed) { "Restaurar e remover o backup temporário requer confirmação explícita." }
        if (!backupDir.exists()) return@withContext
        val rootHome = File(rootfsDir, "root")
        rootHome.mkdirs()
        backupDir.copyRecursively(rootHome, overwrite = true)
        backupDir.deleteRecursively()
        Log.i(TAG, "User data restored from $backupDir")
    }

    /**
     * Ensure session-specific directories exist on the host filesystem.
     */
    fun ensureSessionDirs(sessionId: String) {
        SessionDirectories.ensure(context.filesDir, sessionId)
    }

    /**
     * Read system DNS servers and search domains from ConnectivityManager,
     * then write resolv.conf into the Alpine rootfs.
     * Mirrors iOS ISHKernel.configureDns / refreshDns.
     * Falls back to 8.8.8.8 / 8.8.4.4 if no system DNS available.
     */
    fun refreshDns() {
        if (!rootfsDir.exists()) return

        val resolvConf = StringBuilder()

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val linkProps: LinkProperties? = if (network != null) cm.getLinkProperties(network) else null

            if (linkProps != null) {
                // Search domains
                val domains = linkProps.domains
                if (!domains.isNullOrBlank()) {
                    resolvConf.append("search $domains\n")
                    Log.i(TAG, "[DNS] search domains: $domains")
                }

                // Nameservers
                val dnsServers = linkProps.dnsServers
                if (dnsServers.isNotEmpty()) {
                    for (server in dnsServers) {
                        val addr = server.hostAddress ?: continue
                        resolvConf.append("nameserver $addr\n")
                        Log.i(TAG, "[DNS] system server: $addr")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[DNS] Failed to read system DNS: ${e.message}")
        }

        // Fallback to public DNS if no system servers were found
        if (!resolvConf.contains("nameserver")) {
            Log.i(TAG, "[DNS] no system DNS — using fallback: 8.8.8.8, 8.8.4.4")
            resolvConf.append("nameserver 8.8.8.8\n")
            resolvConf.append("nameserver 8.8.4.4\n")
        }

        try {
            val file = File(rootfsDir, "etc/resolv.conf")
            file.parentFile?.mkdirs()
            file.writeText(resolvConf.toString())
            Log.i(TAG, "[DNS] resolv.conf updated:\n$resolvConf")
        } catch (e: Exception) {
            Log.e(TAG, "[DNS] Failed to write resolv.conf: ${e.message}")
        }
    }

    /**
     * Copy every file under `assets/default_mount/` into the rootfs, preserving
     * the directory layout. Mirrors iOS RootfsManager.applyDefaultMountOverlay
     * (src/ios/iSH/RootfsManager.swift:153-225) — runs on every boot so shipping
     * updated profile scripts / URL wrappers with an app release just works.
     *
     * Files under `bin` / `sbin` paths get the execute bit set. Existing files
     * are overwritten so users see the latest shipped version even if they
     * previously edited the file — matching iOS behavior.
     *
     * No-op when the asset dir is missing or the rootfs hasn't been extracted.
     */
    suspend fun applyDefaultMountOverlay() = withContext(Dispatchers.IO) {
        if (!rootfsDir.exists()) {
            Log.d(TAG, "[DefaultMount] rootfs missing, skipping overlay")
            return@withContext
        }

        // AssetManager.list() returns an empty array for both "leaf file" and
        // "missing path", so probe for children before recursing — otherwise
        // a missing overlay dir would be mistaken for a single leaf file.
        val rootEntries = try {
            context.assets.list(DEFAULT_MOUNT_ASSET)
        } catch (t: Throwable) {
            null
        }
        if (rootEntries.isNullOrEmpty()) {
            Log.d(TAG, "[DefaultMount] no default_mount assets found, skipping overlay")
            return@withContext
        }

        val startNs = System.nanoTime()
        var fileCount = 0
        try {
            fileCount = copyAssetDir(DEFAULT_MOUNT_ASSET, rootfsDir)
        } catch (t: Throwable) {
            Log.w(TAG, "[DefaultMount] overlay failed: ${t.message}", t)
            return@withContext
        }

        // Mirror iOS removeExternallyManagedMarker() — drop PEP 668 marker so
        // `pip install` Just Works inside this embedded Alpine rootfs even
        // when the shipped pip.conf isn't being read (e.g. pip invoked with
        // --isolated or via a venv). Safe: this is a single-tenant sandbox.
        val markerRemoved = removeExternallyManagedMarker()

        // [T-mcp-cli-readonly-android] Make the shipped minis-mcp-cli Python lib
        // read-only inside the guest so a user can't `vi`-tamper the bundled
        // scripts (mirrors iOS #707). Scoped to /usr/local/lib/minis-mcp-cli/
        // ONLY — the wrapper at /usr/local/bin/minis-mcp-cli stays executable +
        // writable (app-managed). Re-applied on every boot AFTER the copy; the
        // copyAssetDir leaf-copy above re-opens read-only files writable first,
        // so the next app-upgrade overlay still overwrites cleanly.
        val lockedCount = lockMcpCliLibReadOnly()

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        Log.i(TAG, "[DefaultMount] Done. $fileCount file(s) overlaid, $markerRemoved EXTERNALLY-MANAGED marker(s) removed, $lockedCount minis-mcp-cli lib path(s) locked read-only in %.1fms".format(elapsedMs))
    }

    /**
     * [T-mcp-cli-readonly-android] Set the `/usr/local/lib/minis-mcp-cli/`
     * subtree read-only for the guest: directories 0555 (read+execute, no
     * write), files 0444 (read-only). Java's File API has no octal chmod, so
     * we use setWritable(false, false) + setReadable(true, false)
     * (+ setExecutable(true, false) on dirs) — the `false` ownerOnly arg makes
     * the permission apply to all users, matching the setReadable style used
     * for libtalloc above. Returns the number of paths adjusted; 0 if the lib
     * dir is absent. Idempotent — the pre-copy unlock in copyAssetDir keeps the
     * upgrade path working across boots.
     */
    private fun lockMcpCliLibReadOnly(): Int {
        val libDir = File(rootfsDir, "usr/local/lib/minis-mcp-cli")
        if (!libDir.isDirectory) return 0
        var count = 0
        // walkBottomUp so child files are locked before their parent dir loses
        // write — dir mode doesn't gate chmod of already-visited children, but
        // bottom-up keeps the intent clear and avoids any traversal surprise.
        for (f in libDir.walkBottomUp()) {
            f.setWritable(false, false)
            f.setReadable(true, false)
            if (f.isDirectory) f.setExecutable(true, false)
            count++
        }
        return count
    }

    /**
     * Remove the PEP 668 EXTERNALLY-MANAGED marker file from every
     * `usr/lib/python3*` directory in the rootfs. Mirrors iOS
     * RootfsManager.removeExternallyManagedMarker (src/ios/iSH/RootfsManager.swift:228-239).
     * Returns the number of markers removed.
     */
    private fun removeExternallyManagedMarker(): Int {
        val usrLib = File(rootfsDir, "usr/lib")
        val children = usrLib.listFiles() ?: return 0
        var removed = 0
        for (entry in children) {
            if (!entry.name.startsWith("python3")) continue
            val marker = File(entry, "EXTERNALLY-MANAGED")
            if (marker.exists() && marker.delete()) {
                Log.i(TAG, "[DefaultMount] Removed EXTERNALLY-MANAGED from ${entry.name}")
                removed++
            }
        }
        return removed
    }

    /**
     * Recursively copy an assets path into [targetBase]. Returns the number of
     * regular files written. Files under /bin/, /sbin/, /usr/bin/, /usr/sbin/,
     * /usr/local/bin/, /usr/local/sbin/ get the execute bit set.
     */
    private fun copyAssetDir(assetPath: String, targetBase: File, prefix: String = ""): Int {
        val entries = context.assets.list(assetPath) ?: return 0
        if (entries.isEmpty()) {
            // Leaf: asset is a file. Copy it.
            val dest = File(targetBase, prefix)
            dest.parentFile?.mkdirs()
            // [T-mcp-cli-readonly-android] A prior boot may have set this file
            // (and its dir) read-only — the minis-mcp-cli lib subtree, locked
            // below. Re-open both writable before overwriting, otherwise an app
            // upgrade can't replace the shipped file: truncating an existing
            // file needs write on the FILE, and creating a new one needs write
            // on the PARENT DIR. No-op when already writable / not yet present.
            dest.parentFile?.setWritable(true, true)
            if (dest.exists()) dest.setWritable(true, true)
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            if (isUnderBinDir(prefix)) {
                dest.setExecutable(true, false)
            }
            return 1
        }
        var count = 0
        for (name in entries) {
            val childAsset = "$assetPath/$name"
            val childPrefix = if (prefix.isEmpty()) name else "$prefix/$name"
            count += copyAssetDir(childAsset, targetBase, childPrefix)
        }
        return count
    }

    private fun isUnderBinDir(relativePath: String): Boolean {
        val normalized = "/$relativePath"
        return BIN_DIR_PREFIXES.any { normalized.startsWith(it) }
    }

    // --- POSIX tar extraction ---

    /**
     * Extract a POSIX tar stream into the target directory.
     * Handles regular files, directories, and symlinks.
     * Does not depend on any external library.
     */
    internal fun extractTar(input: InputStream, targetDir: File) {
        val header = ByteArray(512)
        val root = targetDir.canonicalFile.toPath()
        require(targetDir.isDirectory && !java.nio.file.Files.isSymbolicLink(targetDir.toPath())) { "Diretório de extração inválido." }
        var entries = 0
        var totalBytes = 0L

        while (true) {
            val bytesRead = readFully(input, header)
            require(bytesRead == 512) { "Cabeçalho tar incompleto." }

            // Check for end-of-archive (two consecutive zero blocks)
            if (header.all { it == 0.toByte() }) {
                require(readFully(input, header) == 512 && header.all { it == 0.toByte() }) { "Fim de tar incompleto." }
                // Consume to EOF: this also makes GZIPInputStream verify the CRC and length.
                val trailing = ByteArray(8192)
                var totalTrailing = 0L
                while (true) {
                    val n = input.read(trailing)
                    if (n < 0) break
                    require(n > 0 && (0 until n).all { trailing[it] == 0.toByte() }) { "Dados após o fim do tar." }
                    totalTrailing += n
                    require(totalTrailing <= 1_048_576) { "Preenchimento tar excessivo." }
                }
                break
            }
            val expectedChecksum = extractString(header, 148, 8).trim().toLongOrNull(8)
                ?: throw IllegalArgumentException("Checksum tar inválido.")
            val actualChecksum = header.indices.sumOf { i -> if (i in 148..155) 32L else (header[i].toInt() and 255).toLong() }
            require(expectedChecksum == actualChecksum) { "Checksum tar incorreto." }

            val name = extractString(header, 0, 100)
            val modeOctal = extractString(header, 100, 8)
            val sizeOctal = extractString(header, 124, 12)
            val typeFlag = header[156].toInt().toChar()
            val linkName = extractString(header, 157, 100)
            val mode = modeOctal.trim().toIntOrNull(8) ?: 0

            // Handle GNU/POSIX long names via prefix field (bytes 345-500)
            val prefix = extractString(header, 345, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

            require(fullName.isNotEmpty()) { "Nome tar vazio." }

            // GabiTama: tar is treated as untrusted even when shipped as an APK asset.
            require(++entries <= 50_000) { "Entradas demais no rootfs." }
            require(!fullName.startsWith("/") && !fullName.contains('\u0000')) { "Caminho absoluto inválido no rootfs." }
            val path = root.resolve(fullName).normalize()
            require(path.startsWith(root)) { "Caminho fora do rootfs." }
            if (path == root) {
                require(typeFlag == '5' && (sizeOctal.trim().isEmpty() || sizeOctal.trim().toLongOrNull(8) == 0L)) { "Entrada raiz inválida." }
                continue
            }
            var component = root
            for (part in root.relativize(path.parent)) {
                component = component.resolve(part)
                require(!java.nio.file.Files.isSymbolicLink(component)) { "Diretório simbólico no caminho de extração." }
            }
            require(!java.nio.file.Files.isSymbolicLink(path)) { "Entrada simbólica já existente." }
            val size = sizeOctal.trim().let { if (it.isEmpty()) 0L else it.toLongOrNull(8) }
                ?: throw IllegalArgumentException("Tamanho tar inválido.")
            require(size in 0..268_435_456L && totalBytes <= 536_870_912L - size) { "Rootfs excede limite de extração." }
            totalBytes += size
            val outFile = path.toFile()

            when (typeFlag) {
                '5', 'D' -> {
                    // Directory
                    outFile.mkdirs()
                }
                '2' -> {
                    // Symbolic link
                    outFile.parentFile?.mkdirs()
                    require(linkName.isNotEmpty()) { "Link vazio no rootfs." }
                    // Absolute guest paths are rooted inside Alpine, not the host filesystem.
                    val target = if (linkName.startsWith("/")) root.resolve(linkName.removePrefix("/")).normalize()
                        else path.parent.resolve(linkName).normalize()
                    require(target.startsWith(root)) { "Link fora do rootfs." }
                    java.nio.file.Files.createSymbolicLink(path, path.parent.relativize(target))
                }
                '0', '\u0000' -> {
                    // Regular file (type '0' or null/legacy)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { output ->
                        var remaining = size
                        val buf = ByteArray(8192)
                        while (remaining > 0) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val n = input.read(buf, 0, toRead)
                            require(n > 0) { "Arquivo tar incompleto." }
                            output.write(buf, 0, n)
                            remaining -= n
                        }
                    }
                    // Preserve executable permission from tar header
                    if (mode and 0b001_001_001 != 0) {
                        outFile.setExecutable(true, false)
                    }
                    // Skip padding to next 512-byte boundary
                    val remainder = (size % 512).toInt()
                    if (remainder != 0) {
                        skipFully(input, (512 - remainder).toLong())
                    }
                    continue // Already consumed data + padding
                }
                '1' -> {
                    // Hard link — create a copy
                    outFile.parentFile?.mkdirs()
                    require(!linkName.startsWith("/")) { "Hardlink absoluto inválido." }
                    val linkTarget = root.resolve(linkName).normalize()
                    require(linkTarget.startsWith(root) && java.nio.file.Files.isRegularFile(linkTarget, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { "Hardlink inválido." }
                    var linkParent = root
                    for (part in root.relativize(linkTarget.parent)) {
                        linkParent = linkParent.resolve(part)
                        require(!java.nio.file.Files.isSymbolicLink(linkParent)) { "Hardlink atravessa diretório simbólico." }
                    }
                    linkTarget.toFile().copyTo(outFile, overwrite = false)
                }
                else -> {
                    throw IllegalArgumentException("Tipo tar não suportado: $typeFlag")
                }
            }

            // Skip data blocks for non-file entries that we didn't consume above
            if (typeFlag != '0' && typeFlag != '\u0000' && size > 0) {
                val blocks = (size + 511) / 512 * 512
                skipFully(input, blocks)
            }
        }
    }

    internal fun extractString(header: ByteArray, offset: Int, length: Int): String {
        val end = minOf(offset + length, header.size)
        var actualEnd = offset
        for (i in offset until end) {
            if (header[i] == 0.toByte()) break
            actualEnd = i + 1
        }
        return String(header, offset, actualEnd - offset, Charset.forName("UTF-8"))
    }

    internal fun readFully(input: InputStream, buf: ByteArray): Int {
        var offset = 0
        while (offset < buf.size) {
            val n = input.read(buf, offset, buf.size - offset)
            if (n < 0) return offset
            require(n > 0) { "Fluxo tar sem progresso." }
            offset += n
        }
        return offset
    }

    internal fun skipFully(input: InputStream, count: Long) {
        var remaining = count
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val n = input.read(buf, 0, toRead)
            require(n > 0) { "Padding tar incompleto." }
            remaining -= n
        }
    }

    /**
     * InputStream wrapper that reports cumulative bytes read as a 0..1 fraction.
     * Throttles updates so each +1% bump emits at most once. Designed to wrap
     * the asset stream (not the gzip stream) so progress is monotonic against
     * a size we can cheaply know up front.
     */
    private class ProgressInputStream(
        inner: InputStream,
        private val total: Long,
        private val onProgress: (Float) -> Unit,
    ) : FilterInputStream(inner) {
        private var read: Long = 0
        private var lastReportedPercent: Int = -1

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) { read += 1; publish() }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) { read += n; publish() }
            return n
        }

        override fun skip(n: Long): Long {
            val skipped = super.skip(n)
            if (skipped > 0) { read += skipped; publish() }
            return skipped
        }

        private fun publish() {
            if (total <= 0) return
            val pct = ((read * 100) / total).toInt().coerceIn(0, 99)
            if (pct != lastReportedPercent) {
                lastReportedPercent = pct
                onProgress(pct / 100f)
            }
        }
    }

    companion object {
        private const val TAG = "RootfsManager"
        private const val ARCH = "aarch64"
        private const val ROOTFS_ASSET = "alpine-minirootfs.tar.gz"
        private const val ROOTFS_ASSET_TAR = "alpine-minirootfs.tar"
        private const val PROOT_ASSET = "proot-aarch64"
        private const val DEFAULT_MOUNT_ASSET = "default_mount"

        /**
         * Rootfs paths whose contents must be executable. Matches iOS
         * RootfsManager.swift:199 byte-for-byte so the same overlay tree
         * produces identical file modes on both platforms.
         */
        private val BIN_DIR_PREFIXES = listOf(
            "/bin/",
            "/sbin/",
            "/usr/bin/",
            "/usr/sbin/",
            "/usr/local/bin/",
            "/usr/local/sbin/",
        )

        @Volatile
        private var instance: RootfsManager? = null

        fun getInstance(context: Context): RootfsManager {
            return instance ?: synchronized(this) {
                instance ?: RootfsManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
