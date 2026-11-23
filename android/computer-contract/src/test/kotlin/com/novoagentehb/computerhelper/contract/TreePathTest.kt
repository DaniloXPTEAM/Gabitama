package com.novoagentehb.computerhelper.contract

import org.junit.Assert.assertEquals
import org.junit.Test

class TreePathTest {
    private fun bad(p: String) = assertEquals("should reject: $p", HelperContract.CODE_BAD_PATH, TreePath.validate(p))
    private fun ok(p: String) = assertEquals("should accept: $p", HelperContract.CODE_OK, TreePath.validate(p))

    @Test fun acceptsSimpleRelativePaths() {
        ok("data.txt")
        ok("a/b/c.txt")
        ok("a-b_c/d.e")
    }

    @Test fun rejectsTraversalAbsoluteAndEmpty() {
        bad("")
        bad("/etc/passwd")
        bad("../x")
        bad("a/../../x")
        bad("a/./b")
        bad("a\\b")
        bad("a\u0000b")
    }

    @Test fun rejectsHiddenAndOversizedSegmentsAndDepth() {
        bad(".hidden")
        bad("a/b/c/d/e")
        bad("x".repeat(65))
        bad("a//b")
    }
}
