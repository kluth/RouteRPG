package app.kiloquest

import app.kiloquest.nav.LatLon
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Place(val name: String, val position: LatLon)

private const val USER_AGENT = "KiloQuest/0.1 (private use)"

private suspend fun http(url: String, method: String = "GET", body: String? = null, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code: ${text.take(200)}")
            text
        } finally {
            c.disconnect()
        }
    }

/** Geocoding ueber Photon (OSM-Daten). Oeffentlicher Server nur fuer privaten Gebrauch, spaeter selbst hosten. */
suspend fun searchPlaces(query: String, languageCode: String): List<Place> {
    val q = URLEncoder.encode(query, "UTF-8")
    val lang = languageCode.takeIf { it in setOf("de", "en", "fr", "it") } ?: "en"
    val body = http("https://photon.komoot.io/api/?q=$q&limit=6&lang=$lang")
    val features = JSONObject(body).getJSONArray("features")
    return (0 until features.length()).map { i ->
        val f = features.getJSONObject(i)
        val c = f.getJSONObject("geometry").getJSONArray("coordinates")
        val p = f.getJSONObject("properties")
        val label = listOfNotNull(
            p.optString("name").takeIf { it.isNotBlank() },
            p.optString("street").takeIf { it.isNotBlank() },
            p.optString("city").takeIf { it.isNotBlank() },
            p.optString("country").takeIf { it.isNotBlank() },
        ).distinct().joinToString(", ")
        Place(label.ifBlank { "?" }, LatLon(c.getDouble(1), c.getDouble(0)))
    }
}

suspend fun fetchRoute(requestJson: String): String {
    val base = BuildConfig.VALHALLA_URL.trimEnd('/')
    require(base.isNotBlank()) { "no server" }
    return http(
        "$base/route", "POST", requestJson,
        mapOf("X-Api-Token" to BuildConfig.VALHALLA_TOKEN),
    )
}
