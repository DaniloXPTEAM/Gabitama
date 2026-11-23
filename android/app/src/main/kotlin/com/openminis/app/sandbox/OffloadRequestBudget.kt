package com.openminis.app.sandbox

/** Cumulative cap before allocating caller-provided protocol strings. */
internal class OffloadRequestBudget(private val maxBytes: Int) {
    private var remaining = maxBytes

    init { require(maxBytes >= 0) }

    fun consume(bytes: Int) {
        require(bytes >= 0 && bytes <= remaining) { "offload request too large" }
        remaining -= bytes
    }
}
