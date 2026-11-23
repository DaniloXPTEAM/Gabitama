package com.novoagentehb.app.computer.runtime

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RootfsGrantPolicyTest {
    private lateinit var base: File
    private lateinit var root: File

    @Before
    fun setUp() {
        base = Files.createTempDirectory("rootfs-grant-policy").toFile()
        root = File(base, "root").apply { mkdirs() }
        val outside = File(base, "outside").apply { mkdirs() }
        File(root, "granted").mkdirs()
        File(root, "granted/data.txt").writeText("grant")
        File(outside, "secret.txt").writeText("secret")
        Files.createSymbolicLink(File(root, "link-file.txt").toPath(), File(root, "granted/data.txt").toPath())
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())
        Files.createSymbolicLink(File(root, "granted/escape.txt").toPath(), File(outside, "secret.txt").toPath())
    }

    @After
    fun tearDown() {
        base.deleteRecursively()
    }

    @Test
    fun grantsNestedRegularFile() {
        val decision = RootfsGrantPolicy.resolveRegularFile(root, "granted/data.txt")
        assertTrue(decision is RootfsGrantPolicy.Decision.Granted)
        val granted = (decision as RootfsGrantPolicy.Decision.Granted).file
        assertEquals(File(root, "granted/data.txt").canonicalPath, granted.canonicalPath)
    }

    @Test
    fun rejectsTraversalAndNonCanonicalPaths() {
        val paths = listOf(
            "",
            "/granted/data.txt",
            "../outside/secret.txt",
            "granted/../granted/data.txt",
            "./granted/data.txt",
            "granted//data.txt",
            "granted/data.txt/",
            "granted\\data.txt",
            "granted/da\u0000ta.txt",
        )
        for (path in paths) assertRejected(root, path)
    }

    @Test
    fun rejectsSymbolicLinkAsFinalComponent() {
        assertRejected(root, "link-file.txt")
    }

    @Test
    fun rejectsSymbolicLinkAsIntermediateDirectory() {
        assertRejected(root, "escape/secret.txt")
    }

    @Test
    fun rejectsSymbolicLinkInsideGrantedTree() {
        assertRejected(root, "granted/escape.txt")
    }

    @Test
    fun rejectsDirectoriesMissingEntriesAndNonDirectoryRoot() {
        assertRejected(root, "granted")
        assertRejected(root, "granted/missing.txt")
        assertRejected(root, "granted/data.txt/child")
        val rootLink = File(base, "root-link")
        Files.createSymbolicLink(rootLink.toPath(), root.toPath())
        assertRejected(rootLink, "granted/data.txt")
    }

    @Test
    fun enforcesDepthAndLengthLimits() {
        val deep = (1..RootfsGrantPolicy.MAX_COMPONENTS).joinToString("/") { "d" } + "/f.txt"
        assertRejected(root, deep)
        assertRejected(root, "x".repeat(RootfsGrantPolicy.MAX_PATH_LENGTH + 1))
    }

    private fun assertRejected(rootDir: File, path: String) {
        val decision = RootfsGrantPolicy.resolveRegularFile(rootDir, path)
        assertTrue("accepted: $path", decision is RootfsGrantPolicy.Decision.Rejected)
    }
}
