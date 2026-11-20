package app.kiloquest.nav

/** Dekodiert Google-/Valhalla-kodierte Polylines (Valhalla: Praezision 6). */
object PolylineCodec {
    fun decode(encoded: String, precision: Int = 6): List<LatLon> {
        val factor = Math.pow(10.0, precision.toDouble())
        val result = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var shift = 0
            var acc = 0L
            var b: Int
            do {
                b = encoded[index++].code - 63
                acc = acc or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (acc and 1L != 0L) (acc shr 1).inv() else acc shr 1

            shift = 0
            acc = 0L
            do {
                b = encoded[index++].code - 63
                acc = acc or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20)
            lon += if (acc and 1L != 0L) (acc shr 1).inv() else acc shr 1

            result += LatLon(lat / factor, lon / factor)
        }
        return result
    }
}
