package com.novoagentehb.app.bridges

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class WeatherSummary(
    val cityName: String,
    val tempC: Int,
    val feelsLikeC: Int,
    val humidityPercent: Int,
    val description: String,
    val emoji: String
)

@Singleton
class WeatherClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val cities = mapOf(
        "fortaleza" to Triple(-3.7319, -38.5267, "Fortaleza"),
        "sao paulo" to Triple(-23.5505, -46.6333, "São Paulo"),
        "rio de janeiro" to Triple(-22.9068, -43.1729, "Rio de Janeiro"),
        "rio" to Triple(-22.9068, -43.1729, "Rio de Janeiro"),
        "brasilia" to Triple(-15.7975, -47.8919, "Brasília"),
        "salvador" to Triple(-12.9714, -38.5014, "Salvador"),
        "recife" to Triple(-8.0476, -34.8770, "Recife"),
        "belo horizonte" to Triple(-19.9167, -43.9345, "Belo Horizonte"),
        "curitiba" to Triple(-25.4284, -49.2733, "Curitiba"),
        "porto alegre" to Triple(-30.0346, -51.2177, "Porto Alegre"),
        "manaus" to Triple(-3.1190, -60.0217, "Manaus"),
        "natal" to Triple(-5.7945, -35.2110, "Natal")
    )

    fun resolveCoordinates(cityQuery: String): Triple<Double, Double, String> {
        val norm = Normalizer.normalize(cityQuery.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return cities[norm] ?: cities.entries.firstOrNull { norm.contains(it.key) }?.value
            ?: Triple(-3.7319, -38.5267, "Fortaleza")
    }

    suspend fun getCurrentWeather(cityQuery: String = "Fortaleza"): WeatherSummary? = withContext(Dispatchers.IO) {
        val (lat, lon, displayCity) = resolveCoordinates(cityQuery)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code" +
            "&temperature_unit=celsius&timezone=America%2FFortaleza"
        val req = Request.Builder().url(url).get().build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string().orEmpty()
                val cur = JsonParser.parseString(raw).asJsonObject.getAsJsonObject("current") ?: return@use null
                val temp = cur.get("temperature_2m")?.asDouble ?: 0.0
                val feels = cur.get("apparent_temperature")?.asDouble ?: temp
                val hum = cur.get("relative_humidity_2m")?.asInt ?: 0
                val code = cur.get("weather_code")?.asInt ?: 0
                val (desc, emoji) = describeWmoCode(code)
                WeatherSummary(displayCity, temp.roundToInt(), feels.roundToInt(), hum, desc, emoji)
            }
        }.getOrNull()
    }

    companion object {
        fun describeWmoCode(code: Int): Pair<String, String> = when (code) {
            0 -> "Céu limpo" to "☀️"
            1, 2 -> "Parcialmente nublado" to "⛅"
            3 -> "Nublado" to "☁️"
            45, 48 -> "Neblina" to "🌫️"
            51, 53, 55, 61, 63, 65 -> "Chuva" to "🌧️"
            80, 81, 82 -> "Pancadas de chuva" to "🌦️"
            95, 96, 99 -> "Tempestade" to "⛈️"
            else -> "Tempo estável" to "🌤️"
        }
    }
}
