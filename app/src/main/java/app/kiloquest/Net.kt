package app.kiloquest

import app.kiloquest.nav.AutobahnApi
import app.kiloquest.nav.LatLon
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Place(val name: String, val position: LatLon)

private const val USER_AGENT = "KiloQuest/0.1 (private use)"

private suspend fun http(
    url: String,
    method: String = "GET",
    body: String? = null,
    headers: Map<String, String> = emptyMap(),
    contentType: String = "application/json",
): String =
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
                c.setRequestProperty("Content-Type", contentType)
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

/**
 * Zugang zum eigenen Routing-Server. Der Token liegt nur auf dem Geraet (SharedPreferences, kein Backup) und wird in den
 * Einstellungen eingegeben; die URL darf als Vorgabe aus dem Build kommen, ein Token nur bei `-PembedToken=true`.
 */
object ServerConfig {
    private const val KEY_URL = "server_url"
    private const val KEY_TOKEN = "server_token"
    @Volatile private var urlOverride = ""
    @Volatile private var tokenOverride = ""

    val url: String get() = urlOverride.ifBlank { BuildConfig.VALHALLA_URL }
    val token: String get() = tokenOverride.ifBlank { BuildConfig.VALHALLA_TOKEN }
    val configured: Boolean get() = url.isNotBlank()

    fun load(p: android.content.SharedPreferences) {
        urlOverride = p.getString(KEY_URL, "") ?: ""
        tokenOverride = p.getString(KEY_TOKEN, "") ?: ""
    }

    /** Nur https:// ist erlaubt (der Token darf nie im Klartext uebers Netz). Leere URL = Build-Vorgabe. */
    fun save(p: android.content.SharedPreferences, newUrl: String, newToken: String): Boolean {
        val u = newUrl.trim()
        if (u.isNotEmpty() && !u.startsWith("https://", ignoreCase = true)) return false
        p.edit().putString(KEY_URL, u).putString(KEY_TOKEN, newToken.trim()).apply()
        load(p)
        return true
    }
}

suspend fun fetchRoute(requestJson: String): String = fetchValhalla("route", requestJson)

/** POST an den eigenen Valhalla-Server (`route`, `trace_attributes` ...). */
suspend fun fetchValhalla(endpoint: String, requestJson: String): String {
    val base = ServerConfig.url.trimEnd('/')
    require(base.isNotBlank()) { "no server" }
    return http(
        "$base/$endpoint", "POST", requestJson,
        mapOf("X-Api-Token" to ServerConfig.token),
    )
}

/** Overpass-API (OSM-Daten). Oeffentlicher Server, nur wenige Anfragen pro Fahrt, privat okay. */
suspend fun fetchOverpass(query: String): String =
    http(
        "https://overpass-api.de/api/interpreter", "POST",
        "data=" + URLEncoder.encode(query, "UTF-8"),
        contentType = "application/x-www-form-urlencoded",
    )

/** Autobahn GmbH Verkehrsmeldungen (frei, ohne Schluessel). */
suspend fun fetchAutobahn(road: String, service: String): String = http(AutobahnApi.url(road, service))

/** Einfacher GET (z. B. Wikipedia-API). */
suspend fun fetchUrl(url: String): String = http(url)

/** Landescode (ISO 3166, z. B. "SE") der Position per Photon-Reverse-Geocoding; null bei Fehler/ohne Treffer. */
suspend fun fetchCountryCode(lat: Double, lon: Double): String? = runCatching {
    val body = http("https://photon.komoot.io/reverse?lat=$lat&lon=$lon&limit=1")
    val f = JSONObject(body).getJSONArray("features")
    if (f.length() == 0) null else f.getJSONObject(0).getJSONObject("properties").optString("countrycode").takeIf { it.isNotBlank() }
}.getOrNull()

/** Ortsname (Stadt/Ort/Name) einer Position per Photon-Reverse; null bei Fehler. */
suspend fun fetchPlaceName(lat: Double, lon: Double, languageCode: String): String? = runCatching {
    val body = http("https://photon.komoot.io/reverse?lat=$lat&lon=$lon&limit=1&lang=$languageCode")
    val f = JSONObject(body).getJSONArray("features")
    if (f.length() == 0) return@runCatching null
    val pr = f.getJSONObject(0).getJSONObject("properties")
    listOf("city", "town", "village", "name", "county").firstNotNullOfOrNull { k -> pr.optString(k).takeIf { it.isNotBlank() } }
}.getOrNull()

/** Tierbeobachtungen (GBIF, nur CC0/CC BY) rund um eine auf 0,1 Grad gerundete Position; leer bei Fehler. */
suspend fun fetchWildlife(lat: Double, lon: Double): List<app.kiloquest.nav.GbifOccurrence> = runCatching {
    app.kiloquest.nav.GbifParser.parse(http(app.kiloquest.nav.GbifParser.buildUrl(lat, lon)))
}.getOrDefault(emptyList())

/** ISS-Bahndaten (TLE) von CelesTrak; die Anfrage ist global und enthaelt keine Position. Leer bei Fehler. */
suspend fun fetchIssTle(): String = runCatching { http("https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=tle") }.getOrDefault("")
