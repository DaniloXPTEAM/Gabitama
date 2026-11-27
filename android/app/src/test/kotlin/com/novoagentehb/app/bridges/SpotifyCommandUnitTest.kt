package com.novoagentehb.app.bridges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Interpretação e resultados do Spotify, sem Android. Cenários A, B e C do defeito. */
class SpotifyCommandUnitTest {

    @Test
    fun cenarioA_abrirSpotifyEhHandoffNaoBusca() {
        val cmd = SpotifyCommandParser.parse("Gabi, abre o Spotify.")
        assertEquals(SpotifyCommandParser.Command.Open("Spotify"), cmd)
        val r = SpotifyOutcomeRules.forOpen(opened = true, query = null, failureMessage = null)
        assertTrue(r.success)
        assertFalse("abrir o app não é verificação", r.verified)
        assertTrue(r.message.contains("Nada foi reproduzido"))
    }

    @Test
    fun cenarioB_procurarNaoViraReproducaoNemTextoDoVerbo() {
        val cmd = SpotifyCommandParser.parse("Gabi, procura uma campanha de RPG no Spotify")
        assertEquals(SpotifyCommandParser.Command.Search("campanha de rpg"), cmd)
        assertFalse(cmd is SpotifyCommandParser.Command.PlayRequest)
    }

    @Test
    fun cenarioC_tocarSemIntegracaoRecusaComMotivo() {
        val cmd = SpotifyCommandParser.parse("Gabi, toca minha playlist no Spotify")
        assertEquals(SpotifyCommandParser.Command.PlayRequest("minha playlist"), cmd)
        val r = SpotifyOutcomeRules.forPlayRequest("minha playlist")
        assertFalse(r.success)
        assertFalse(r.verified)
        assertEquals(SpotifyOutcomeRules.PLAYBACK_UNAVAILABLE, r.errorCode)
        assertTrue(r.message.contains("Nada foi reproduzido"))
    }

    @Test
    fun pesquisaComAcentoEVerboPesquisa() {
        assertEquals(SpotifyCommandParser.Command.Search("musica x"), SpotifyCommandParser.parse("pesquisa música X no spotify"))
        assertEquals(SpotifyCommandParser.Command.Open("Spotify"), SpotifyCommandParser.parse("Abrir Spotify"))
        assertEquals(SpotifyCommandParser.Command.Open("Spotify"), SpotifyCommandParser.parse("spotify"))
    }

    @Test
    fun gabiTamaNaoEhConfundidoComWakeWord() {
        assertEquals(SpotifyCommandParser.Command.NotSpotify, SpotifyCommandParser.parse("gabitama, abre o youtube"))
        assertEquals(SpotifyCommandParser.Command.NotSpotify, SpotifyCommandParser.parse("conversa livre qualquer"))
    }

    @Test
    fun buscaSemTermoNaoAbreNada() {
        val r = SpotifyOutcomeRules.forBlankQuery()
        assertFalse(r.success)
        assertEquals("SPOTIFY_QUERY_EMPTY", r.errorCode)
    }

    @Test
    fun falhaDeAberturaNaoViraSucesso() {
        val r = SpotifyOutcomeRules.forOpen(opened = false, query = "x", failureMessage = "sem app")
        assertFalse(r.success)
        assertFalse(r.verified)
        assertEquals("sem app", r.message)
    }

    @Test
    fun offlineIntentMapsSpotifyCommandsToTheRightTool() {
        val open = AndroidDeviceBridge.parseOfflineIntent("Gabi, abre o Spotify.")
        assertEquals("open_app", open?.first)
        assertEquals("Spotify", open?.second?.get("app_name"))

        val search = AndroidDeviceBridge.parseOfflineIntent("procura uma campanha de RPG no Spotify")
        assertEquals("search_spotify", search?.first)
        assertEquals("campanha de rpg", search?.second?.get("query"))
        assertNull(search?.second?.get("mode"))

        val play = AndroidDeviceBridge.parseOfflineIntent("toca minha playlist no Spotify")
        assertEquals("search_spotify", play?.first)
        assertEquals("play", play?.second?.get("mode"))
    }
}
