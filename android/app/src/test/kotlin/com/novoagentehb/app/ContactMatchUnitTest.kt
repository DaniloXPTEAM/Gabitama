package com.novoagentehb.app

import com.novoagentehb.app.bridges.ContactMatch
import org.junit.Assert.*
import org.junit.Test

class ContactMatchUnitTest {
    @Test fun matchesExactNameNotSimilarSubstring() {
        val contacts = listOf("Secretária Luciane" to "+55 11 2222", "Luciane" to "+55 11 1111")
        assertEquals("+55 11 1111", ContactMatch.uniqueNumber("Luciane", contacts))
        assertNull(ContactMatch.uniqueNumber("Luci", contacts))
        assertNull(ContactMatch.uniqueNumber("", contacts))
        assertNull(ContactMatch.uniqueNumber("Luciane", contacts.take(1)))
    }
    @Test fun multipleNumbersAreAmbiguousAndDuplicateRowsAreNot() {
        assertNull(ContactMatch.uniqueNumber("Ana", listOf("Ana" to "111", "Ana" to "222")))
        assertEquals("111", ContactMatch.uniqueNumber("Ana", listOf("Ana" to "111", "Ána" to "1 1 1")))
    }
}
