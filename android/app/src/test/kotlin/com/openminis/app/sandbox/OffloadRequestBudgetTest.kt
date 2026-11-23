package com.openminis.app.sandbox

import org.junit.Assert.fail
import org.junit.Test

class OffloadRequestBudgetTest {
    @Test fun cumulativeLengthCannotOverflowOrExceedLimit() {
        val budget = OffloadRequestBudget(10)
        budget.consume(4)
        budget.consume(6)
        budget.consume(0)
        for (invalid in listOf(1, -1, Int.MAX_VALUE)) {
            try { budget.consume(invalid); fail("accepted invalid length") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
