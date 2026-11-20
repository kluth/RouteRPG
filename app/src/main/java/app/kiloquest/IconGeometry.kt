package app.kiloquest

// ERZEUGT von tools/icon/build_icon.py, nicht von Hand aendern.

/** Geometrie (108er-Raster) und Farben des App-Icons, genutzt fuer das dynamische grosse Benachrichtigungs-Icon. */
object IconGeometry {
    const val PIN = "M54,83 C44,72 34,60 34,45 A20,20 0 0 1 74,45 C74,60 64,72 54,83 Z"
    const val CIRCLE_CX = 54f
    const val CIRCLE_CY = 45f
    const val CIRCLE_R = 12.2f
    const val ROAD = "M-6,100 C26,86 22,66 54,64 S104,46 116,22"

    class Pal(val bg1: Int, val bg2: Int, val pin1: Int, val pin2: Int, val navy: Int, val glyph: Int, val glow: Float)

    val PALETTES: Map<String, Pal> = mapOf(
        "DAY" to Pal(0xFF1768E8.toInt(), 0xFF0A2A5C.toInt(), 0xFFFFC83A.toInt(), 0xFFFF7A00.toInt(), 0xFF0A2A5C.toInt(), 0xFFFFD54A.toInt(), 0.22f),
        "DUSK" to Pal(0xFFFF7E5F.toInt(), 0xFF5B1A8F.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE0B2.toInt(), 0xFF5B1A8F.toInt(), 0xFFFFB300.toInt(), 0.25f),
        "NIGHT" to Pal(0xFF17243D.toInt(), 0xFF04080F.toInt(), 0xFFFFD35C.toInt(), 0xFFFF9500.toInt(), 0xFF101B30.toInt(), 0xFFFFE27A.toInt(), 0.1f),
        "FOREST" to Pal(0xFF2FA463.toInt(), 0xFF073A2B.toInt(), 0xFFFFC83A.toInt(), 0xFFFF7A00.toInt(), 0xFF073A2B.toInt(), 0xFFFFD54A.toInt(), 0.2f),
        "MONO" to Pal(0xFF444B55.toInt(), 0xFF101317.toInt(), 0xFFFFFFFF.toInt(), 0xFFC5CBD4.toInt(), 0xFF101317.toInt(), 0xFFFFFFFF.toInt(), 0.14f),
        "ALERT" to Pal(0xFFE5483F.toInt(), 0xFF7A0F12.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE3E0.toInt(), 0xFF7A0F12.toInt(), 0xFFFFD54A.toInt(), 0.22f),
    )

    val GLYPHS: Map<String, String> = mapOf(
        "star" to "M54.00,37.10 L56.17,42.41 L61.89,42.84 L57.52,46.54 L58.88,52.11 L54.00,49.10 L49.12,52.11 L50.48,46.54 L46.11,42.84 L51.83,42.41 Z",
        "arrow" to "M54,37.2 L60.2,52.2 L54,48.7 L47.8,52.2 Z",
        "pause" to "M49.2,39.2 H52.6 V50.8 H49.2 Z M55.4,39.2 H58.8 V50.8 H55.4 Z",
        "wc" to "M47.800000000000004,39.8 a1.8,1.8 0 1 0 3.6,0 a1.8,1.8 0 1 0 -3.6,0 Z M47.7,42.4 H51.5 V48.8 H47.7 Z M56.6,39.8 a1.8,1.8 0 1 0 3.6,0 a1.8,1.8 0 1 0 -3.6,0 Z M58.4,42.4 L61.7,49.4 H55.1 Z",
        "camera" to "M47.4,41.6 H50.6 L51.8,39.6 H56.2 L57.4,41.6 H60.6 V50.6 H47.4 Z M50.9,46 a3.1,3.1 0 1 0 6.2,0 a3.1,3.1 0 1 0 -6.2,0 Z",
        "calendar" to "M47.4,39.2 H60.6 V51.6 H47.4 Z M49.1,42.6 H58.9 V50 H49.1 Z M50.4,37.6 H51.8 V40.6 H50.4 Z M56.2,37.6 H57.6 V40.6 H56.2 Z M50.6,44.2 H52.6 V46.2 H50.6 Z M53,44.2 H55 V46.2 H53 Z M55.4,44.2 H57.4 V46.2 H55.4 Z",
        "warning" to "M54,37.4 L61.6,50.8 H46.4 Z M53.2,42.4 H54.8 V47.4 H53.2 Z M53.0,49.1 a1.0,1.0 0 1 0 2.0,0 a1.0,1.0 0 1 0 -2.0,0 Z",
        "cross" to "M52.3,38.4 H55.7 V43.3 H60.6 V46.7 H55.7 V51.6 H52.3 V46.7 H47.4 V43.3 H52.3 Z",
        "book" to "M53.4,40.6 C51.2,39.2 48.9,38.9 46.7,39.5 V50.7 C48.9,50.1 51.2,50.4 53.4,51.8 Z M54.6,40.6 C56.8,39.2 59.1,38.9 61.3,39.5 V50.7 C59.1,50.1 56.8,50.4 54.6,51.8 Z",
        "flag" to "M48.6,37.8 H50.2 V52.4 H48.6 Z M50.2,38.4 H60.4 L57.2,42.7 L60.4,47 H50.2 Z",
        "moon" to "M57,37.6 A8.2,8.2 0 1 0 57,52.4 A6.6,6.6 0 1 1 57,37.6 Z",
        "snow" to "M46.40,46.15 L61.60,46.15 L61.60,44.25 L46.40,44.25 Z M49.38,39.09 L56.98,52.26 L58.62,51.31 L51.02,38.14 Z M56.98,38.14 L49.38,51.31 L51.02,52.26 L58.62,39.09 Z",
        "sun" to "M50.7,45.2 a3.3,3.3 0 1 0 6.6,0 a3.3,3.3 0 1 0 -6.6,0 Z M59.40,46.10 L62.30,46.10 L62.30,44.30 L59.40,44.30 Z M57.18,49.65 L59.23,51.71 L60.51,50.43 L58.45,48.38 Z M53.10,50.60 L53.10,53.50 L54.90,53.50 L54.90,50.60 Z M49.55,48.38 L47.49,50.43 L48.77,51.71 L50.82,49.65 Z M48.60,44.30 L45.70,44.30 L45.70,46.10 L48.60,46.10 Z M50.82,40.75 L48.77,38.69 L47.49,39.97 L49.55,42.02 Z M54.90,39.80 L54.90,36.90 L53.10,36.90 L53.10,39.80 Z M58.45,42.02 L60.51,39.97 L59.23,38.69 L57.18,40.75 Z",
    )
}
