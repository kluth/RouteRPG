package app.kiloquest

import android.content.Context
import app.kiloquest.nav.GeoBox
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/** OpenFreeMap: freie Vektorkacheln ohne API-Key (privat okay; fuer eine Veroeffentlichung Offline-PMTiles selbst hosten). */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Laedt Kartenkacheln boxweise fuer das Offline-"Reise-Paket". Die Regionen liegen im Offline-Speicher von MapLibre
 * und werden vom Kartenbetrachter automatisch genutzt, wenn kein Netz da ist.
 */
class OfflinePackage(private val context: Context) {
    private val manager = OfflineManager.getInstance(context)

    init {
        // Standard-Limit (6000 Kacheln) ist fuer lange Strecken zu klein
        manager.setOfflineMapboxTileCountLimit(200_000L)
    }

    /**
     * Laedt die [boxes] nacheinander. [onProgress] liefert (fertige Boxen + Anteil der aktuellen, Gesamtboxen);
     * [onFinished] meldet Erfolg oder Fehler.
     */
    fun download(
        boxes: List<GeoBox>,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (Double) -> Unit,
        onFinished: (Boolean) -> Unit,
    ) {
        if (boxes.isEmpty()) { onFinished(true); return }
        fun next(i: Int) {
            if (i >= boxes.size) { onFinished(true); return }
            val b = boxes[i]
            val def = OfflineTilePyramidRegionDefinition(
                MAP_STYLE_URL, LatLngBounds.from(b.north, b.east, b.south, b.west),
                minZoom.toDouble(), maxZoom.toDouble(), context.resources.displayMetrics.density,
            )
            manager.createOfflineRegion(def, "kiloquest-trip-$i".toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val frac = if (status.requiredResourceCount > 0) status.completedResourceCount.toDouble() / status.requiredResourceCount else 0.0
                            onProgress((i + frac.coerceIn(0.0, 1.0)) / boxes.size)
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                next(i + 1)
                            }
                        }
                        override fun onError(error: OfflineRegionError) { onFinished(false) }
                        override fun mapboxTileCountLimitExceeded(limit: Long) { onFinished(false) }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }
                override fun onError(error: String) { onFinished(false) }
            })
        }
        next(0)
    }

    /** Loescht alle Offline-Karten dieser App. */
    fun clear(onDone: () -> Unit) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val list = offlineRegions.orEmpty()
                if (list.isEmpty()) { onDone(); return }
                var left = list.size
                for (r in list) r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() { if (--left == 0) onDone() }
                    override fun onError(error: String) { if (--left == 0) onDone() }
                })
            }
            override fun onError(error: String) { onDone() }
        })
    }
}
