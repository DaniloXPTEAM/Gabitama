package com.novoagentehb.app.gabi

/**
 * Lembra que a voz foi pausada porque o GabiTama saiu do primeiro plano (ex.: abriu o Spotify).
 * A pausa é explícita: ao voltar, a Gabi avisa e só retoma a escuta quando a pessoa tocar no
 * microfone. Nunca grava em segundo plano nem contorna as regras de microfone do Android.
 *
 * Implementação própria do GabiTama (MIT). Sem Android; testável na JVM.
 */
object VoicePauseMemory {

    private val lock = Any()
    private var paused = false

    fun markPaused() = synchronized(lock) { paused = true }

    /** Retorna verdadeiro uma única vez por pausa; a segunda chamada devolve falso. */
    fun consumePaused(): Boolean = synchronized(lock) {
        val was = paused
        paused = false
        was
    }

    fun clear() = synchronized(lock) { paused = false }

    fun resumeNotice(): String =
        "Pausei a escuta e a conversa de voz quando você saiu do GabiTama, para não gravar em segundo plano. " +
            "Toque no microfone para retomar. Nada foi perdido do que já foi escrito aqui."
}
