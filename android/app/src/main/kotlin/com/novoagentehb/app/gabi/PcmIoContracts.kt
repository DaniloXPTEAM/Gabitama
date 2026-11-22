package com.novoagentehb.app.gabi

/** Android audio APIs report errors as negative codes rather than exceptions. */
internal object PcmIoContracts {
    fun requireBufferSize(size: Int): Int {
        check(size > 0) { "Formato de áudio não suportado pelo dispositivo." }
        return size
    }

    fun requireReadResult(count: Int): Int {
        check(count >= 0) { "Falha de leitura do microfone (código $count)." }
        return count
    }

    /** Write all bytes, tolerating partial writes but not error or zero-progress loops. */
    inline fun writeFully(size: Int, write: (offset: Int, remaining: Int) -> Int) {
        var offset = 0
        while (offset < size) {
            val written = write(offset, size - offset)
            check(written > 0 && written <= size - offset) { "Falha de reprodução de áudio (código $written)." }
            offset += written
        }
    }
}
