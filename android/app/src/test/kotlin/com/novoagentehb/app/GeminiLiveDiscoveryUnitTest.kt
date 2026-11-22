package com.novoagentehb.app

import com.novoagentehb.app.agent.models.GeminiLiveDiscovery
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GeminiLiveDiscoveryUnitTest {
    private fun client(server: MockWebServer) = OkHttpClient.Builder().addInterceptor { chain ->
        val original = chain.request().url
        val url = server.url(original.encodedPath).newBuilder().encodedQuery(original.encodedQuery).build()
        chain.proceed(chain.request().newBuilder().url(url).build())
    }.build()

    @Test fun paginatesFiltersAndDeduplicatesWithoutLeakingKeyIntoUrl() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"models":[{"name":"models/one","supportedGenerationMethods":["bidiGenerateContent"]},{"name":"models/chat","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"next+/="}"""))
            server.enqueue(MockResponse().setBody("""{"models":[{"name":"models/one","supportedGenerationMethods":["bidiGenerateContent"]},{"name":"models/two","supportedGenerationMethods":["bidiGenerateContent"]}]}"""))
            server.start()
            assertEquals(listOf("one", "two"), GeminiLiveDiscovery.list(client(server), "synthetic-key"))
            val first = server.takeRequest(); val second = server.takeRequest()
            assertEquals("synthetic-key", first.getHeader("x-goog-api-key"))
            assertFalse(first.path!!.contains("synthetic-key"))
            assertEquals("next+/=", second.requestUrl!!.queryParameter("pageToken"))
        }
    }
    @Test fun emptyAndHttpFailuresAndRepeatedTokensAreNotSuccessfulPartialResults() {
        MockWebServer().use { server ->
            server.start(); val http = client(server)
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(GeminiLiveDiscovery.list(http, "synthetic-key").isEmpty())
            for (code in listOf(401, 403, 429, 500)) {
                server.enqueue(MockResponse().setResponseCode(code).setBody("private secret"))
                val e = runCatching { GeminiLiveDiscovery.list(http, "synthetic-key") }.exceptionOrNull()
                assertNotNull(e); assertTrue(e!!.message!!.contains(code.toString())); assertFalse(e.message!!.contains("private"))
            }
            server.enqueue(MockResponse().setBody("""{"nextPageToken":"same"}"""))
            server.enqueue(MockResponse().setBody("""{"nextPageToken":"same"}"""))
            assertTrue(runCatching { GeminiLiveDiscovery.list(http, "synthetic-key") }.isFailure)
            server.enqueue(MockResponse().setBody("not json"))
            assertTrue(runCatching { GeminiLiveDiscovery.list(http, "synthetic-key") }.isFailure)
        }
    }
}
