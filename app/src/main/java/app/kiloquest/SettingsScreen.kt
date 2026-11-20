package app.kiloquest

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.game.AgeBand
import app.kiloquest.ui.ChoiceDialog
import app.kiloquest.ui.ConfirmDialog
import app.kiloquest.ui.InfoDialog
import app.kiloquest.ui.SettingDivider
import app.kiloquest.ui.SettingRow
import app.kiloquest.ui.SettingSwitch
import app.kiloquest.ui.SettingsSection
import app.kiloquest.ui.screenColumn

private enum class Dlg { NONE, CORRIDOR, DAILY, THEME, AGE, PLAYERS, OFFLINE, RECENTS, MEMORY, RESET, PRIVACY, LICENSES }

/** Einstellungen: alles, was sich dauerhaft einstellen laesst, mit Abfragen vor dem Loeschen. */
@Composable
fun SettingsScreen(state: UiState, nav: NavController) {
    val ctx = LocalContext.current
    val s = state.settings
    var dialog by rememberSaveable { mutableStateOf(Dlg.NONE) }
    var picker by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (picker) picker = false else nav.showSettings(false) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) nav.setVoiceMode(true) }

    if (picker) {
        app.kiloquest.ui.AvatarPicker(state.avatar, nav::setAvatar) { picker = false }
        return
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.screenColumn().padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = { nav.showSettings(false) }, shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = Modifier.size(48.dp)) {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.kids_back), modifier = Modifier.size(24.dp))
                    }
                }
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            }

            SettingsSection(stringResource(R.string.sec_display)) {
                SettingRow(
                    stringResource(R.string.set_theme),
                    value = stringResource(themeLabel(s.theme)),
                ) { dialog = Dlg.THEME }
            }

            SettingsSection(stringResource(R.string.sec_navigation)) {
                SettingSwitch(stringResource(R.string.set_navi_voice), stringResource(R.string.set_navi_voice_hint), s.naviVoice) { v -> nav.updateSettings { it.copy(naviVoice = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_speed_limit), null, s.showSpeedLimit) { v -> nav.updateSettings { it.copy(showSpeedLimit = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_lane_hints), null, s.laneHints) { v -> nav.updateSettings { it.copy(laneHints = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_detours), stringResource(R.string.set_detours_hint), s.detourSuggestions) { v -> nav.updateSettings { it.copy(detourSuggestions = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_weather), stringResource(R.string.set_weather_hint), s.weatherHints) { v -> nav.updateSettings { it.copy(weatherHints = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_breaks), stringResource(R.string.set_breaks_hint), s.breakSuggestions) { v -> nav.updateSettings { it.copy(breakSuggestions = v) } }
                SettingDivider()
                SettingRow(stringResource(R.string.set_toilet_corridor), stringResource(R.string.set_toilet_corridor_hint), value = stringResource(R.string.corridor_km, s.toiletCorridorKm)) { dialog = Dlg.CORRIDOR }
                SettingDivider()
                SettingRow(stringResource(R.string.set_daily_hours), stringResource(R.string.set_daily_hours_hint), value = stringResource(R.string.daily_hours_value, s.dailyDriveHours)) { dialog = Dlg.DAILY }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_keep_screen), null, s.keepScreenOn) { v -> nav.updateSettings { it.copy(keepScreenOn = v) } }
            }

            SettingsSection(stringResource(R.string.sec_kids)) {
                SettingRow(stringResource(R.string.kids_age), value = stringResource(ageLabel(state.ageBand))) { dialog = Dlg.AGE }
                SettingDivider()
                SettingRow(stringResource(R.string.set_players), value = state.players.toString()) { dialog = Dlg.PLAYERS }
                SettingDivider()
                SettingRow(stringResource(R.string.set_avatar), value = state.avatar) { picker = true }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_kilo_voice), stringResource(R.string.set_kilo_voice_hint), s.kiloVoice) { v -> nav.updateSettings { it.copy(kiloVoice = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_live_quests), stringResource(R.string.set_live_quests_hint), s.liveQuests) { v -> nav.updateSettings { it.copy(liveQuests = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.kids_sickness_mode), stringResource(R.string.set_sickness_hint), state.sicknessMode, onChange = nav::setSicknessMode)
                SettingDivider()
                SettingSwitch(stringResource(R.string.voice_mode), stringResource(if (state.voiceAvailable) R.string.set_voice_hint else R.string.voice_unavailable), state.voiceMode, state.voiceAvailable) { on ->
                    if (!on) nav.setVoiceMode(false)
                    else if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) nav.setVoiceMode(true)
                    else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_real_map), stringResource(R.string.set_real_map_hint), state.realMap, onChange = nav::setRealMap)
            }

            SettingsSection(stringResource(R.string.sec_car)) {
                SettingSwitch(stringResource(R.string.car_simple_map), stringResource(R.string.car_simple_map_hint), state.carSimpleMap, onChange = nav::setCarSimpleMap)
            }

            SettingsSection(stringResource(R.string.sec_devices)) {
                SettingRow(stringResource(R.string.set_pair), stringResource(R.string.set_pair_hint)) { nav.showSettings(false); nav.showParent(true) }
            }

            SettingsSection(stringResource(R.string.sec_data)) {
                SettingRow(stringResource(R.string.pkg_clear), stringResource(R.string.set_offline_hint), destructive = true) { dialog = Dlg.OFFLINE }
                SettingDivider()
                SettingRow(stringResource(R.string.set_clear_recents), destructive = true) { dialog = Dlg.RECENTS }
                SettingDivider()
                SettingRow(stringResource(R.string.set_reset_memory), stringResource(R.string.set_reset_memory_hint), destructive = true) { dialog = Dlg.MEMORY }
                SettingDivider()
                SettingRow(stringResource(R.string.set_reset_all), destructive = true) { dialog = Dlg.RESET }
                SettingDivider()
                SettingRow(stringResource(R.string.set_privacy)) { dialog = Dlg.PRIVACY }
            }

            SettingsSection(stringResource(R.string.sec_about)) {
                SettingRow(stringResource(R.string.set_version), value = versionName(ctx)) { }
                SettingDivider()
                SettingRow(stringResource(R.string.set_licenses), stringResource(R.string.set_licenses_hint)) { dialog = Dlg.LICENSES }
            }
        }
    }

    val cancel = stringResource(R.string.dlg_cancel)
    val close = stringResource(R.string.dlg_close)
    when (dialog) {
        Dlg.NONE -> Unit
        Dlg.CORRIDOR -> ChoiceDialog(
            stringResource(R.string.set_toilet_corridor),
            listOf(
                Triple(1, stringResource(R.string.corridor_km, 1), stringResource(R.string.corridor_1_hint)),
                Triple(2, stringResource(R.string.corridor_km, 2), stringResource(R.string.corridor_2_hint)),
                Triple(4, stringResource(R.string.corridor_km, 4), stringResource(R.string.corridor_4_hint)),
            ),
            s.toiletCorridorKm, { v -> nav.updateSettings { it.copy(toiletCorridorKm = v) } }, { dialog = Dlg.NONE }, close,
        )
        Dlg.DAILY -> ChoiceDialog(
            stringResource(R.string.set_daily_hours),
            listOf(
                Triple(4, stringResource(R.string.daily_hours_value, 4), stringResource(R.string.daily_4_hint)),
                Triple(6, stringResource(R.string.daily_hours_value, 6), stringResource(R.string.daily_6_hint)),
                Triple(8, stringResource(R.string.daily_hours_value, 8), stringResource(R.string.daily_8_hint)),
                Triple(10, stringResource(R.string.daily_hours_value, 10), stringResource(R.string.daily_10_hint)),
            ),
            s.dailyDriveHours, { v -> nav.updateSettings { it.copy(dailyDriveHours = v) } }, { dialog = Dlg.NONE }, close,
        )
        Dlg.THEME -> ChoiceDialog(
            stringResource(R.string.set_theme),
            ThemeMode.entries.map { Triple(it, stringResource(themeLabel(it)), null) },
            s.theme, { v -> nav.updateSettings { it.copy(theme = v) } }, { dialog = Dlg.NONE }, close,
        )
        Dlg.AGE -> ChoiceDialog(
            stringResource(R.string.kids_age),
            AgeBand.entries.map { Triple(it, stringResource(ageLabel(it)), stringResource(ageHint(it))) },
            state.ageBand, nav::setAgeBand, { dialog = Dlg.NONE }, close,
        )
        Dlg.PLAYERS -> ChoiceDialog(
            stringResource(R.string.set_players),
            (1..4).map { Triple(it, it.toString(), if (it == 1) stringResource(R.string.set_players_hint) else null) },
            state.players, nav::setPlayers, { dialog = Dlg.NONE }, close,
        )
        Dlg.OFFLINE -> ConfirmDialog(stringResource(R.string.pkg_clear), stringResource(R.string.dlg_offline_msg), stringResource(R.string.dlg_delete), cancel, onConfirm = nav::clearOfflineMaps, onDismiss = { dialog = Dlg.NONE })
        Dlg.RECENTS -> ConfirmDialog(stringResource(R.string.set_clear_recents), stringResource(R.string.dlg_recents_msg), stringResource(R.string.dlg_delete), cancel, onConfirm = nav::clearRecents, onDismiss = { dialog = Dlg.NONE })
        Dlg.MEMORY -> ConfirmDialog(stringResource(R.string.set_reset_memory), stringResource(R.string.dlg_memory_msg), stringResource(R.string.dlg_reset), cancel, onConfirm = nav::resetQuestMemory, onDismiss = { dialog = Dlg.NONE })
        Dlg.RESET -> ConfirmDialog(stringResource(R.string.set_reset_all), stringResource(R.string.dlg_reset_msg), stringResource(R.string.dlg_reset), cancel, onConfirm = nav::resetSettings, onDismiss = { dialog = Dlg.NONE })
        Dlg.PRIVACY -> InfoDialog(stringResource(R.string.set_privacy), stringResource(R.string.privacy_text), close) { dialog = Dlg.NONE }
        Dlg.LICENSES -> InfoDialog(stringResource(R.string.set_licenses), stringResource(R.string.licenses_text), close) { dialog = Dlg.NONE }
    }
}

private fun themeLabel(t: ThemeMode) = when (t) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun ageLabel(a: AgeBand) = when (a) {
    AgeBand.MINI -> R.string.age_mini
    AgeBand.EXPLORER -> R.string.age_explorer
    AgeBand.PRO -> R.string.age_pro
}

private fun ageHint(a: AgeBand) = when (a) {
    AgeBand.MINI -> R.string.age_mini_hint
    AgeBand.EXPLORER -> R.string.age_explorer_hint
    AgeBand.PRO -> R.string.age_pro_hint
}

private fun versionName(ctx: android.content.Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
