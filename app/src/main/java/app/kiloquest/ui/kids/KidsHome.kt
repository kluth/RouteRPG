package app.kiloquest.ui.kids

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiloquest.KidsActions
import app.kiloquest.KiloQuestApp
import app.kiloquest.R
import app.kiloquest.StoryCardView
import app.kiloquest.ToiletPhase
import app.kiloquest.UiState
import app.kiloquest.game.AgeBand
import app.kiloquest.game.AudioGameType
import app.kiloquest.game.Quest
import app.kiloquest.game.QuestKind
import app.kiloquest.game.StickerBook
import app.kiloquest.questSpeech
import app.kiloquest.stringByKey
import app.kiloquest.ui.AlarmYellow
import app.kiloquest.ui.KidMapCard
import app.kiloquest.ui.OnAlarmYellow
import app.kiloquest.ui.StickerGrid
import app.kiloquest.ui.StickerImage
import app.kiloquest.ui.TeamGoalBar
import app.kiloquest.ui.ToiletStatusCard
import app.kiloquest.ui.questEmoji
import app.kiloquest.ui.rememberWindowInfo
import kotlinx.coroutines.delay

/**
 * Neue Kinderansicht fuer alle Altersstufen: oben Status (Sterne, Sticker, Fortschritt als Punkte), in der Mitte genau eine Sache
 * (Aufgabe, Lob, Sticker oder Kilo im Ruhezustand), unten eine feste Leiste mit Klo, Karte und "Mehr". Alles Weitere liegt in "Mehr".
 * Die Kleinsten (4-6) bekommen die groessten Ziele, Kilo spricht vor; die Aelteren ein dichteres Layout. Es gibt nie ein "Falsch".
 */
@Composable
fun KidsHome(state: UiState, nav: KidsActions, onOpenMap: () -> Unit, onPickAvatar: () -> Unit) {
    val ctx = LocalContext.current
    // Einstellung "Ruhige Ansicht" dieses Geraets (in Tests ohne App-Klasse: aus)
    val own = (ctx.applicationContext as? KiloQuestApp)?.nav?.state?.collectAsState()?.value
    val style = rememberKidsStyle(state.ageBand)
    val reduced = rememberReducedMotion(own?.settings?.calmView == true, state.navigating && state.curvy)
    var more by rememberSaveable { mutableStateOf(false) }
    BackHandler(more) { more = false }
    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        Surface(Modifier.fillMaxSize(), color = style.bg) {
            if (more) {
                KidsMore(state, nav, style, onClose = { more = false }, onOpenMap = { more = false; onOpenMap() }, onPickAvatar = onPickAvatar)
            } else {
                KidsMain(state, nav, style, onOpenMap, onPickAvatar, onMore = { more = true })
            }
        }
    }
}

@Composable
private fun KidsMain(state: UiState, nav: KidsActions, style: KidsStyle, onOpenMap: () -> Unit, onPickAvatar: () -> Unit, onMore: () -> Unit) {
    val ctx = LocalContext.current
    val win = rememberWindowInfo()
    val speak = rememberSpeaker()
    val q = state.quest
    val fb = state.feedback
    val calm = state.navigating && state.curvy
    val mini = state.ageBand == AgeBand.MINI

    // Die Kleinsten auf ihrem eigenen Geraet: Kilo liest jede neue Aufgabe von allein vor (auf dem Hauptgeraet spricht die Navigation selbst)
    LaunchedEffect(q) {
        if (q != null && state.remote && mini) { val t = ctx.questSpeech(q, withOptions = true); if (!nav.speakAloud(t)) speak(t) }
    }
    val lookOut = stringResource(R.string.sickness_look_out)
    LaunchedEffect(calm) { if (calm && state.remote) { delay(600); if (!nav.speakAloud(lookOut)) speak(lookOut) } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Status: Sterne, Sticker-Regal, Fortschritt
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StarPill(state.points, style)
            StickerShelf(StickerBook.ALL, state.album, style, showCount = !mini)
            Spacer(Modifier.weight(1f))
            if (state.questsTotal > 0 && !calm) ProgressTrail(state.questsDone, state.questsTotal, style)
        }

        if (state.linkLost) LinkLostBanner(style)

        val mainContent: @Composable () -> Unit = {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ToiletStatusCard(state.toilet, {}, showActions = false)
                StoryCardView(state.storyCard, nav)
                when {
                    calm -> CalmCard(style, lookOut)
                    q != null -> QuestCard(state, q, style, nav, speak)
                    fb != null -> FeedbackCard(state, fb, style, nav)
                    else -> IdleCard(state, style, nav, onMore)
                }
                // Explorer/Profi: kleine Karte unter der Aufgabe; Mini: Karte nur ueber die Leiste (ruhiger Bildschirm)
                if (!mini && !calm && !win.twoPane) KidMapCard(state, onOpenMap, onPickAvatar, height = 140.dp)
                if (state.players > 1 && !mini) Scoreboard(state, style)
            }
        }
        if (win.twoPane) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { mainContent() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!calm) KidMapCard(state, onOpenMap, onPickAvatar, height = 360.dp)
                    state.teamGoal?.let { TeamGoalBar(it) }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { mainContent() }
        }
        BottomBar(state, nav, style, onOpenMap, onMore)
    }
}

/** Verbindung zum Auto weg: ruhiger Hinweis, kein Fehlerton; der Spielstand bleibt sichtbar, die App verbindet sich von selbst neu. */
@Composable
private fun LinkLostBanner(style: KidsStyle) {
    Surface(shape = RoundedCornerShape(24.dp), color = style.container, contentColor = style.text, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).politeLive()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KiloAvatar(KiloMood.SLEEPY, 56.dp)
            Text(stringResource(R.string.kids_link_lost), style = kidsText(if (style.band == AgeBand.MINI) 22 else 18), color = style.text, modifier = Modifier.weight(1f))
        }
    }
}

/** Reiseuebelkeit: ruhiger Bildschirm, nur Kilo und der Blick nach draussen. */
@Composable
private fun CalmCard(style: KidsStyle, lookOut: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp).politeLive(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KiloAvatar(KiloMood.SLEEPY, 180.dp)
        Text(lookOut, style = kidsText(28), color = style.text, textAlign = TextAlign.Center)
        Text("🌳 👀 🌄", style = TextStyle(fontSize = 44.sp), modifier = Modifier.clearAndSetSemantics { })
    }
}

private val optionColors = listOf(Color(0xFF0B57D0), Color(0xFFFF9F43), Color(0xFF5FCB8C))
private val optionShapes = listOf(R.drawable.ic_shape_circle, R.drawable.ic_shape_square, R.drawable.ic_shape_triangle)

@Composable
private fun QuestCard(state: UiState, q: Quest, style: KidsStyle, nav: KidsActions, speak: (String) -> Unit) {
    val ctx = LocalContext.current
    val mini = style.band == AgeBand.MINI
    val deb = rememberDebouncer(q)
    val read = { ctx.questSpeech(q, withOptions = true).let { t -> if (!nav.speakAloud(t)) speak(t) } }
    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (mini && q.kind == QuestKind.CHOICE) 10.dp else 14.dp)) {
            if (state.players > 1) {
                Text(stringResource(R.string.kids_turn_marker, stringResource(R.string.kids_player, state.currentPlayer + 1)), style = kidsText(20), color = style.text)
            }
            val disc = if (mini && q.kind != QuestKind.CHOICE) 132.dp else if (mini) 88.dp else 92.dp
            Box(Modifier.size(disc).background(style.container, CircleShape).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                Text(questEmoji(q), style = TextStyle(fontSize = (disc.value * 0.55f).sp))
            }
            Text(ctx.stringByKey(q.textKey, q.args), style = kidsText(style.questSp), textAlign = TextAlign.Center, modifier = Modifier.asHeading().politeLive())
            if (state.listening) Text(stringResource(R.string.voice_listening), color = style.text, style = kidsText(18))
            // Nochmal vorlesen: nur Symbol (spart Platz, TalkBack liest die Beschriftung)
            val readLabel = stringResource(R.string.mini_read_aloud)
            Surface(
                onClick = { deb.run(block = read) }, shape = CircleShape, color = style.container, contentColor = style.text, shadowElevation = 3.dp,
                modifier = Modifier.size(maxOf(style.target, 72.dp)).semantics { contentDescription = readLabel; role = Role.Button },
            ) { Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_volume_up), contentDescription = null, modifier = Modifier.size(40.dp)) } }
            if (q.kind == QuestKind.CHOICE) {
                // Kein Lesen noetig und nie nur Farbe: jede Antwort hat Form (Kreis, Quadrat, Dreieck) und Farbe
                q.optionKeys.forEachIndexed { i, key ->
                    val c = optionColors[i % 3]
                    BigActionButton(
                        ctx.stringByKey(key), { nav.answerQuest(i) }, style.copy(buttonHeight = 84.dp), modifier = Modifier.fillMaxWidth(), container = c, debouncer = deb,
                        leading = {
                            Box(Modifier.size(52.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(painterResource(optionShapes[i % 3]), contentDescription = null, tint = c, modifier = Modifier.size(30.dp))
                            }
                        },
                    )
                }
            } else {
                BigActionButton(
                    stringResource(if (mini) R.string.mini_yes else R.string.kids_found), { nav.answerQuest() }, style, modifier = Modifier.fillMaxWidth(),
                    icon = painterResource(R.drawable.ic_check_circle), labelSp = if (mini) 38 else 26, debouncer = deb,
                )
            }
            BigActionButton(
                stringResource(if (mini) R.string.mini_not_yet else R.string.kids_skip), nav::skipQuest, style, kind = ButtonKind.SOFT,
                icon = painterResource(R.drawable.ic_skip_next), modifier = Modifier.padding(top = 6.dp), debouncer = deb,
            )
            // Quellenhinweise (Lizenzpflicht): klein am Rand
            val src = when {
                q.type == app.kiloquest.game.QuestType.SKY && q.args.isNotEmpty() && q.textKey != app.kiloquest.game.PlaneSpotting.MOON_KEY -> R.string.sky_source
                q.type == app.kiloquest.game.QuestType.NATURE && q.args.isNotEmpty() -> R.string.nature_source
                q.textKey == app.kiloquest.game.WikiStories.TEXT_KEY -> R.string.story_source
                else -> null
            }
            src?.let { Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun FeedbackCard(state: UiState, fb: app.kiloquest.QuestFeedback, style: KidsStyle, nav: KidsActions) {
    val ctx = LocalContext.current
    val mini = style.band == AgeBand.MINI
    val deb = rememberDebouncer(fb)
    val right = fb.correctOptionKey == null || fb.correct
    val msg = when {
        mini || right -> if (mini) stringResource(R.string.mini_great) else if (fb.correctOptionKey == null) stringResource(R.string.kids_done, fb.points) else stringResource(R.string.kids_correct, fb.points)
        else -> stringResource(R.string.kids_wrong, ctx.stringByKey(fb.correctOptionKey!!))
    }
    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                KiloBubble(msg, style, mood = if (right || mini) KiloMood.CHEER else KiloMood.THINKING, size = if (mini) 110.dp else 88.dp, modifier = Modifier.politeLive())
                val sticker = state.newSticker
                if (sticker != null) {
                    StickerImage(sticker, collected = true, size = if (mini) 180.dp else 120.dp)
                    Text(stringResource(R.string.kids_new_sticker), style = kidsText(28), color = style.text, textAlign = TextAlign.Center)
                } else if (mini) {
                    StarPill(fb.points, style)
                }
                BigActionButton(stringResource(R.string.kids_next), nav::dismissFeedback, style, modifier = Modifier.fillMaxWidth(), icon = painterResource(R.drawable.ic_arrow_forward), labelSp = if (mini) 34 else 26, debouncer = deb)
            }
            if (state.newSticker != null) ConfettiBurst(state.newSticker!!.id, Modifier.matchParentSizeSafe())
        }
    }
}

private fun Modifier.matchParentSizeSafe(): Modifier = this.fillMaxWidth().height(220.dp)

@Composable
private fun IdleCard(state: UiState, style: KidsStyle, nav: KidsActions, onMore: () -> Unit) {
    val text = stringResource(if (state.route == null && !state.remote) R.string.kids_need_route else R.string.kids_waiting)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KiloBubble(text, style, mood = KiloMood.THINKING, size = if (style.band == AgeBand.MINI) 140.dp else 100.dp)
        // Wenn gerade nichts zu tun ist: Angebote statt leerer Bildschirm
        BigActionButton(stringResource(R.string.kids_tile_series), nav::tellSeries, style, modifier = Modifier.fillMaxWidth(), leading = { Text("🎧", style = TextStyle(fontSize = 34.sp)) }, kind = ButtonKind.PRIMARY, labelSp = 24)
        BigActionButton(stringResource(R.string.kids_tile_games), onMore, style, modifier = Modifier.fillMaxWidth(), leading = { Text("🎲", style = TextStyle(fontSize = 34.sp)) }, kind = ButtonKind.SECONDARY, labelSp = 24)
    }
}

@Composable
private fun Scoreboard(state: UiState, style: KidsStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.scores.forEachIndexed { i, pts ->
            val active = state.quest != null && i == state.currentPlayer
            // Nicht nur Farbe: der aktive Spieler hat zusaetzlich ein Dreieck davor und fetteren Text
            Text(
                (if (active) "▶ " else "") + stringResource(R.string.kids_player, i + 1) + ": " + stringResource(R.string.kids_points, pts),
                style = kidsText(if (active) 22 else 18, if (active) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Medium),
                color = if (active) style.text else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Feste untere Leiste: Klo (immer am selben Platz, Sperre gegen Dauertippen), Karte, Mehr. */
@Composable
private fun BottomBar(state: UiState, nav: KidsActions, style: KidsStyle, onOpenMap: () -> Unit, onMore: () -> Unit) {
    var sent by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sent) { if (sent) { delay(60_000); sent = false } }
    val alarm = state.toilet?.phase.let { it != null && it != ToiletPhase.NONE }
    val deb = rememberDebouncer("bar")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (sent && !alarm) Text(stringResource(R.string.kids_toilet_wait), Modifier.padding(bottom = 6.dp).politeLive(), style = kidsText(16, androidx.compose.ui.text.font.FontWeight.Bold), color = style.text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BarButton(
                stringResource(R.string.kids_bar_toilet), { if (!alarm && !sent && !state.linkLost) { sent = true; nav.requestToilet() } }, style, Modifier.weight(1f),
                icon = painterResource(R.drawable.ic_wc),
                container = if (alarm) AlarmYellow else MaterialTheme.colorScheme.errorContainer,
                content = if (alarm) OnAlarmYellow else MaterialTheme.colorScheme.onErrorContainer,
                debouncer = deb,
            )
            BarButton(stringResource(R.string.kids_bar_map), onOpenMap, style, Modifier.weight(1f), icon = painterResource(R.drawable.ic_place), debouncer = deb)
            BarButton(stringResource(R.string.kids_bar_more), onMore, style, Modifier.weight(1f), icon = painterResource(R.drawable.ic_apps), container = style.accent, content = style.onAccent, debouncer = deb)
        }
    }
}

/** "Mehr": Sticker, Geschichten, Spiele, Figur, Karte; Verlassen nur mit Halten. */
@Composable
internal fun KidsMore(state: UiState, nav: KidsActions, style: KidsStyle, onClose: () -> Unit, onOpenMap: () -> Unit, onPickAvatar: () -> Unit) {
    var section by rememberSaveable { mutableStateOf("") }
    BackHandler(section.isNotEmpty()) { section = "" }
    val mini = style.band == AgeBand.MINI
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (section == "games") R.string.kids_games_title else if (section == "album") R.string.kids_tile_album else R.string.kids_more_title),
                Modifier.weight(1f).asHeading(), style = kidsText(30), color = style.text,
            )
            Surface(
                onClick = { if (section.isNotEmpty()) section = "" else onClose() }, shape = CircleShape, color = style.accent, contentColor = style.onAccent, shadowElevation = 3.dp,
                modifier = Modifier.size(maxOf(style.target, 72.dp)),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.kids_tile_back), modifier = Modifier.size(32.dp)) }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (section) {
                "album" -> {
                    Text(stringResource(R.string.kids_album, state.album.size, StickerBook.ALL.size), style = kidsText(20, androidx.compose.ui.text.font.FontWeight.Bold))
                    StickerGrid(StickerBook.ALL, state.album, size = 64.dp)
                }
                "games" -> {
                    if (state.audioGame != null) {
                        Text(stringResource(R.string.game_running), style = kidsText(22), color = style.text)
                        BigActionButton(stringResource(R.string.game_stop), { nav.stopGame(); onClose() }, style, Modifier.fillMaxWidth(), debouncer = rememberDebouncer("stop"))
                    } else {
                        val ctx = LocalContext.current
                        val deb = rememberDebouncer("games")
                        // Spiele laufen auf dem Hauptgeraet; nur dort braucht Kilo das Mikrofon. Ohne Erlaubnis: nachfragen, bei "Nein" ruhiger Hinweis fuer Erwachsene
                        var pending by rememberSaveable { mutableStateOf<String?>(null) }
                        var micDenied by rememberSaveable { mutableStateOf(false) }
                        val mic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
                            val p = pending; pending = null
                            if (ok && p != null) { nav.startGame(AudioGameType.valueOf(p)); onClose() } else micDenied = true
                        }
                        val needsMic = !state.remote
                        fun start(t: AudioGameType) {
                            val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (t == AudioGameType.STORY_RELAY || !needsMic || granted) { nav.startGame(t); onClose() }
                            else { pending = t.name; mic.launch(android.Manifest.permission.RECORD_AUDIO) }
                        }
                        if (micDenied) Surface(shape = RoundedCornerShape(20.dp), color = style.container, modifier = Modifier.fillMaxWidth().politeLive()) {
                            Text(stringResource(R.string.kids_no_mic), Modifier.padding(14.dp), style = kidsText(if (mini) 20 else 17), color = style.text)
                        }
                        listOf(
                            "🔗" to (R.string.game_word_chain to AudioGameType.WORD_CHAIN), "🔤" to (R.string.game_letters to AudioGameType.LETTER_CATEGORY),
                            "🎵" to (R.string.game_rhyme to AudioGameType.RHYME), "📚" to (R.string.game_story to AudioGameType.STORY_RELAY),
                        ).forEach { (emoji, p) ->
                            BigActionButton(stringResource(p.first), { start(p.second) }, style, Modifier.fillMaxWidth(), leading = { Text(emoji, style = TextStyle(fontSize = 34.sp)) }, kind = ButtonKind.SECONDARY, labelSp = 24, debouncer = deb)
                        }
                    }
                }
                else -> {
                    val tiles = buildList {
                        add(Triple("🏆", R.string.kids_tile_album) { section = "album" })
                        add(Triple("🎧", R.string.kids_tile_series) { nav.tellSeries(); onClose() })
                        add(Triple("🎲", R.string.kids_tile_games) { section = "games" })
                        add(Triple("🦊", R.string.kids_tile_figure) { onPickAvatar() })
                        add(Triple("🗺️", R.string.kids_bar_map) { onOpenMap() })
                        if (!state.remote) add(Triple("📖", R.string.kids_tile_story) { nav.tellStory(); onClose() })
                    }
                    tiles.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (emoji, label, click) -> Tile(emoji, stringResource(label), click, style, mini, Modifier.weight(1f)) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    state.teamGoal?.let { TeamGoalBar(it, Modifier.padding(top = 8.dp)) }
                    if (!mini) state.weather?.destination?.let { d ->
                        val t = d.tempC?.let { Math.round(it).toInt().toString() + " °C, " }.orEmpty()
                        Text(stringResource(R.string.dash_dest_weather, t + stringResource(app.kiloquest.weatherConditionRes(d.condition))), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        HoldToLeave(stringResource(R.string.kids_hold_to_leave), stringResource(R.string.kids_hold_hint), style, onDone = { nav.showKids(false) })
    }
}

@Composable
private fun Tile(emoji: String, label: String, onClick: () -> Unit, style: KidsStyle, mini: Boolean, modifier: Modifier = Modifier) {
    val deb = rememberDebouncer(label)
    Surface(
        onClick = { deb.run(block = onClick) }, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp,
        modifier = modifier.heightIn(min = if (mini) 150.dp else 120.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(emoji, style = TextStyle(fontSize = if (mini) 56.sp else 44.sp), modifier = Modifier.clearAndSetSemantics { })
            Text(label, style = kidsText(if (mini) 20 else 18), color = style.text, textAlign = TextAlign.Center)
        }
    }
}
