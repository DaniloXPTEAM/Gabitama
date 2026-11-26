package com.novoagentehb.app.gabi

/**
 * Lógica pura de reprodução das sequências `assets/avatar/<pasta>/`.
 *
 * Separada da composição para ser testável na JVM. Não altera o visual: apenas decide
 * quais quadros existem, em que ordem tocam, quando cair no fallback estático e o
 * intervalo entre quadros.
 */
object AvatarFramePlanner {

    /** Quantos quadros decodificados podem ficar à frente da exibição (limita memória). */
    const val PREFETCH_FRAMES = 4

    /** Formatos aceitos. WebP é o preferencial; PNG continua aceito por compatibilidade. */
    private val SUPPORTED_EXTENSIONS = listOf(".webp", ".png")

    sealed interface Plan {
        /** Nenhum quadro utilizável: usar a imagem estática de fallback. */
        data object Fallback : Plan

        /** Movimento reduzido: mostrar apenas o primeiro quadro, parado. */
        data class Still(val file: String) : Plan

        /** Reprodução em loop, na ordem [files]. */
        data class Loop(val files: List<String>) : Plan
    }

    /** Filtra e ordena os nomes de arquivo que são quadros de animação. */
    fun frameNames(allFiles: List<String>): List<String> =
        allFiles
            .filter { name -> SUPPORTED_EXTENSIONS.any { name.endsWith(it) } }
            .sorted()

    fun plan(allFiles: List<String>, reduceMotion: Boolean): Plan {
        val frames = frameNames(allFiles)
        if (frames.isEmpty()) return Plan.Fallback
        return if (reduceMotion) Plan.Still(frames.first()) else Plan.Loop(frames)
    }

    /** Intervalo mínimo de 16 ms (≈60 fps) e fps sempre ≥ 1. */
    fun frameDelayMs(fps: Int): Long = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(16L)

    /** Próximo índice em loop circular. */
    fun nextIndex(current: Int, size: Int): Int =
        if (size <= 0) 0 else (current + 1) % size

    /**
     * Quantos quadros consecutivos podem falhar na decodificação antes de desistir da
     * sequência e cair no fallback. Evita laço ocupado quando nenhum quadro decodifica.
     */
    fun maxConsecutiveFailures(frameCount: Int): Int = frameCount.coerceAtLeast(1)
}
