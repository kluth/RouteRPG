package app.kiloquest.ui.kids

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.fetchWildlife
import app.kiloquest.game.AgeBand
import app.kiloquest.game.PlateBingoGame
import app.kiloquest.game.PlateCountries
import app.kiloquest.game.PlateCountry
import app.kiloquest.game.WildlifeQuestion
import app.kiloquest.game.WildlifeQuiz
import app.kiloquest.game.WildlifeSpecies
import app.kiloquest.nav.Sky
import app.kiloquest.stringByKey

/** Landesname in der Sprache des Geraets (kein eigener Text noetig). */
private fun countryName(c: PlateCountry): String = java.util.Locale("", c.iso2).getDisplayCountry(java.util.Locale.getDefault())

private const val PREFS = "kq"
private const val KEY_COLLECTION = "plate_collection"
private const val KEY_SEED = "plate_seed"
private const val KEY_MARKED = "plate_marked"
private const val KEY_POINTS = "plate_points"

private fun loadPlates(ctx: Context): PlateBingoGame {
    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val seed = p.getLong(KEY_SEED, 1L)
    val collection = (p.getString(KEY_COLLECTION, "") ?: "").split(',').filter { it.isNotBlank() }.toSet()
    val marked = (p.getString(KEY_MARKED, "") ?: "").split(',').mapNotNull { it.toIntOrNull() }.toSet()
    return PlateBingoGame.start(seed, collection).copy(marked = marked, points = p.getInt(KEY_POINTS, 0))
}

private fun savePlates(ctx: Context, g: PlateBingoGame) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putLong(KEY_SEED, g.card.seed).putString(KEY_COLLECTION, g.collection.joinToString(","))
        .putString(KEY_MARKED, g.marked.joinToString(",")).putInt(KEY_POINTS, g.points).apply()
}

/** Kennzeichen-Bingo und Laender-Sammlung: 3x3-Karte, Tippen auf die Flagge hakt ab; Sammlung bleibt ueber Karten hinweg. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlatesSection(style: KidsStyle) {
    val ctx = LocalContext.current
    var game by remember { mutableStateOf(loadPlates(ctx)) }
    var lastNew by remember { mutableStateOf(false) }
    val deb = rememberDebouncer("plates")
    fun spot(c: PlateCountry) {
        val r = game.spot(c.code)
        lastNew = r.newCountry
        game = r.game
        savePlates(ctx, r.game)
    }
    val mini = style.band == AgeBand.MINI
    Text(stringResource(R.string.plates_hint), style = kidsText(if (mini) 20 else 17), color = style.text, modifier = Modifier.politeLive())
    Text(stringResource(R.string.plates_collection, game.collectionCount, game.collectionTotal), style = kidsText(18, androidx.compose.ui.text.font.FontWeight.Bold), color = style.text)
    if (lastNew) Text(stringResource(R.string.plates_new_country), style = kidsText(20, androidx.compose.ui.text.font.FontWeight.ExtraBold), color = style.text, modifier = Modifier.politeLive())
    if (game.hasBingo) Text(stringResource(R.string.plates_bingo) + if (game.isFull) " 🎉" else "", style = kidsText(28, androidx.compose.ui.text.font.FontWeight.ExtraBold), color = style.text, modifier = Modifier.politeLive())
    game.card.cells.chunked(3).forEachIndexed { r, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { c, country ->
                val idx = r * 3 + c
                val on = idx in game.marked
                val seenWord = stringResource(if (on) R.string.plates_seen else R.string.plates_not_seen)
                Surface(
                    onClick = { deb.run { spot(country) } }, shape = RoundedCornerShape(20.dp),
                    color = if (on) style.accent else MaterialTheme.colorScheme.surface, contentColor = if (on) style.onAccent else style.text,
                    shadowElevation = 3.dp, modifier = Modifier.weight(1f).heightIn(min = maxOf(style.target, 88.dp))
                        .semantics { contentDescription = countryName(country); stateDescription = seenWord },
                ) {
                    Column(Modifier.padding(6.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(country.flag, style = TextStyle(fontSize = 34.sp))
                        // Nicht nur Farbe: Haken am gesehenen Land
                        Text((if (on) "✓ " else "") + countryName(country), style = kidsText(13), textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
    if (game.hasBingo) BigActionButton(stringResource(R.string.plates_next_card), { game = game.nextCard(System.nanoTime()); lastNew = false; savePlates(ctx, game) }, style, Modifier.fillMaxWidth(), debouncer = deb)
    Text(stringResource(R.string.plates_other), style = kidsText(18, androidx.compose.ui.text.font.FontWeight.Bold), color = style.text, modifier = Modifier.padding(top = 8.dp).asHeading())
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in PlateCountries.ALL) {
            val seen = c.code in game.collection
            Surface(
                onClick = { deb.run { spot(c) } }, shape = RoundedCornerShape(16.dp),
                color = if (seen) style.container else MaterialTheme.colorScheme.surface, shadowElevation = 2.dp,
                modifier = Modifier.heightIn(min = style.target).semantics { contentDescription = countryName(c); stateDescription = if (seen) "✓" else "" },
            ) {
                Text((if (seen) "✓ " else "") + c.flag + " " + c.code, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = kidsText(16), color = style.text)
            }
        }
    }
}

private fun speciesName(ctx: Context, s: WildlifeSpecies): String {
    val byKey = ctx.stringByKey(s.nameKey)
    return if (byKey != s.nameKey) byKey else s.displayName ?: s.scientificName
}

/** Tierwelt-Quiz "Was lebt hier?": Beobachtungen aus der Naehe (GBIF), sonst Tiere von ueberall. Mini sieht nie ein "Falsch". */
@Composable
internal fun WildlifeSection(state: UiState, style: KidsStyle) {
    val ctx = LocalContext.current
    var questions by remember { mutableStateOf<List<WildlifeQuestion>?>(null) }
    var offline by remember { mutableStateOf(false) }
    var idx by rememberSaveable { mutableIntStateOf(0) }
    var answered by rememberSaveable { mutableIntStateOf(-1) }
    val pos = state.position
    LaunchedEffect(pos?.let { Math.round(it.lat * 10) to Math.round(it.lon * 10) }) {
        val seed = System.nanoTime()
        val observed = if (pos != null) fetchWildlife(pos.lat, pos.lon) else emptyList()
        offline = observed.isEmpty()
        val species = observed.map { WildlifeSpecies(it.species, it.vernacularName, it.license) }
        questions = WildlifeQuiz.generate(species, seed, 5)
    }
    val qs = questions
    val mini = style.band == AgeBand.MINI
    if (qs == null) { Text(stringResource(R.string.wild_loading), style = kidsText(20), color = style.text, modifier = Modifier.politeLive()); return }
    if (offline) Text(stringResource(R.string.wild_offline), style = kidsText(15), color = style.text)
    if (idx >= qs.size) {
        Text(stringResource(R.string.wild_done), style = kidsText(26, androidx.compose.ui.text.font.FontWeight.ExtraBold), color = style.text, modifier = Modifier.politeLive())
    } else {
        val q = qs[idx]
        Text(ctx.stringByKey(q.textKey), style = kidsText(if (mini) 26 else 22), color = style.text, modifier = Modifier.asHeading())
        val deb = rememberDebouncer(q)
        q.options.forEachIndexed { i, sp ->
            val picked = answered == i
            val show = answered >= 0
            val mark = if (show && q.isCorrect(i)) "✓ " else if (show && picked && !mini) "• " else ""
            BigActionButton(
                mark + speciesName(ctx, sp), { if (answered < 0) answered = i }, style, Modifier.fillMaxWidth(),
                kind = if (show && q.isCorrect(i)) ButtonKind.PRIMARY else ButtonKind.SECONDARY, labelSp = 22, debouncer = deb,
            )
        }
        if (answered >= 0) {
            val text = if (mini || q.isCorrect(answered)) stringResource(R.string.mini_great) else stringResource(R.string.kids_wrong, speciesName(ctx, q.correct))
            Text(text, style = kidsText(22, androidx.compose.ui.text.font.FontWeight.Bold), color = style.text, modifier = Modifier.politeLive())
            BigActionButton(stringResource(R.string.wild_next), { idx += 1; answered = -1 }, style, Modifier.fillMaxWidth(), debouncer = deb)
        }
    }
    Text(stringResource(R.string.wild_source), style = kidsText(12), color = style.text)
}

private fun compassKey(az: Double): Int = when ((((az + 22.5) % 360 + 360) % 360 / 45).toInt()) {
    0 -> R.string.dir_n; 1 -> R.string.dir_ne; 2 -> R.string.dir_e; 3 -> R.string.dir_se
    4 -> R.string.dir_s; 5 -> R.string.dir_sw; 6 -> R.string.dir_w; else -> R.string.dir_nw
}

/** Himmel heute Abend: Mond und helle Planeten mit Richtung und Hoehe, lokal berechnet (kein Netz). */
@Composable
internal fun SkySection(state: UiState, style: KidsStyle) {
    val ctx = LocalContext.current
    val pos = state.position
    if (pos == null) { Text(stringResource(R.string.sky_need_position), style = kidsText(20), color = style.text); return }
    val entries = remember(pos.lat.toInt(), pos.lon.toInt()) { Sky.tonight(System.currentTimeMillis() / 1000, pos.lat, pos.lon) }
    val moon = remember(pos.lat.toInt(), pos.lon.toInt()) { Sky.moon(System.currentTimeMillis() / 1000, pos.lat, pos.lon) }
    Text("🌙 " + ctx.stringByKey(moon.phaseKey), style = kidsText(24, androidx.compose.ui.text.font.FontWeight.Bold), color = style.text)
    if (entries.isEmpty()) Text(stringResource(R.string.sky_none), style = kidsText(20), color = style.text)
    entries.forEach { e ->
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.sky_entry, ctx.stringByKey(e.key), stringResource(compassKey(e.position.azimuthDeg)), e.position.altitudeDeg.toInt()),
                Modifier.padding(16.dp), style = kidsText(if (style.band == AgeBand.MINI) 22 else 18), color = style.text,
            )
        }
    }
    Text(stringResource(R.string.sky_note), style = kidsText(14), color = style.text)
}
