package app.kiloquest

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.remember
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

private enum class Dlg { NONE, CAMERAS, CORRIDOR, DAILY, THEME, AGE, PLAYERS, OFFLINE, RECENTS, MEMORY, RESET, PRIVACY, LICENSES, SERVER, POWER, STORAGE, VOICE_HELP, CLOUD_WARN, APP_ICON }

/** Einstellungen: alles, was sich dauerhaft einstellen laesst, mit Abfragen vor dem Loeschen. */
@Composable
fun SettingsScreen(state: UiState, nav: NavController) {
    val ctx = LocalContext.current
    val s = state.settings
    var dialog by rememberSaveable { mutableStateOf(Dlg.NONE) }
    var picker by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (picker) picker = false else nav.showSettings(false) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) nav.setVoiceMode(true) }
    var voiceStatus by rememberSaveable { mutableStateOf(VoiceAnswers.Status.OK) }
    val micForCommands = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            voiceStatus = VoiceAnswers.status(ctx)
            if (voiceStatus == VoiceAnswers.Status.OK) nav.setVoiceCommands(true) else dialog = Dlg.VOICE_HELP
        } else { voiceStatus = VoiceAnswers.Status.NO_MIC; dialog = Dlg.VOICE_HELP }
    }
    val ctxForMic = androidx.compose.ui.platform.LocalContext.current

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
                SettingDivider()
                SettingRow(stringResource(R.string.set_app_icon), stringResource(R.string.set_app_icon_hint), value = stringResource(AppIcon.current(ctx).labelRes)) { dialog = Dlg.APP_ICON }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_dyn_icons), stringResource(R.string.set_dyn_icons_hint), s.dynamicNotifIcons) { v -> nav.updateSettings { it.copy(dynamicNotifIcons = v) } }
            }

            SettingsSection(stringResource(R.string.sec_navigation)) {
                SettingSwitch(stringResource(R.string.set_navi_voice), stringResource(R.string.set_navi_voice_hint), s.naviVoice) { v -> nav.updateSettings { it.copy(naviVoice = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_speed_limit), null, s.showSpeedLimit) { v -> nav.updateSettings { it.copy(showSpeedLimit = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_avoid_tolls), null, s.avoidTolls) { v -> nav.updateSettings { it.copy(avoidTolls = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_avoid_highways), null, s.avoidHighways) { v -> nav.updateSettings { it.copy(avoidHighways = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_avoid_ferries), null, s.avoidFerries) { v -> nav.updateSettings { it.copy(avoidFerries = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_voice_commands), stringResource(R.string.set_voice_commands_hint), s.voiceCommands) { v ->
                    if (!v) nav.setVoiceCommands(false)
                    else {
                        // Erst pruefen, warum es nicht gehen koennte, und das erklaeren (statt den Schalter stumm zurueckspringen zu lassen)
                        voiceStatus = VoiceAnswers.status(ctxForMic)
                        when (voiceStatus) {
                            VoiceAnswers.Status.OK -> nav.setVoiceCommands(true)
                            VoiceAnswers.Status.NO_MIC -> micForCommands.launch(android.Manifest.permission.RECORD_AUDIO)
                            else -> dialog = Dlg.VOICE_HELP
                        }
                    }
                }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_cloud_speech), stringResource(R.string.set_cloud_speech_hint), s.cloudSpeech) { v ->
                    if (v) dialog = Dlg.CLOUD_WARN else nav.updateSettings { it.copy(cloudSpeech = false) }
                }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_photo_prompts), stringResource(R.string.set_photo_prompts_hint), s.photoPrompts) { v -> nav.updateSettings { it.copy(photoPrompts = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_photos_gallery), stringResource(R.string.set_photos_gallery_hint), s.photosToGallery) { v -> nav.updateSettings { it.copy(photosToGallery = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_overspeed), stringResource(R.string.set_overspeed_hint), s.overspeedWarning) { v -> nav.updateSettings { it.copy(overspeedWarning = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_lane_hints), null, s.laneHints) { v -> nav.updateSettings { it.copy(laneHints = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_detours), stringResource(R.string.set_detours_hint), s.detourSuggestions) { v -> nav.updateSettings { it.copy(detourSuggestions = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_weather), stringResource(R.string.set_weather_hint), s.weatherHints) { v -> nav.updateSettings { it.copy(weatherHints = v) } }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_breaks), stringResource(R.string.set_breaks_hint), s.breakSuggestions) { v -> nav.updateSettings { it.copy(breakSuggestions = v) } }
                SettingDivider()
                SettingRow(stringResource(R.string.set_cameras), stringResource(R.string.set_cameras_hint), value = stringResource(R.string.cameras_off)) { dialog = Dlg.CAMERAS }
                SettingDivider()
                SettingRow(stringResource(R.string.set_toilet_corridor), stringResource(R.string.set_toilet_corridor_hint), value = stringResource(R.string.corridor_km, s.toiletCorridorKm)) { dialog = Dlg.CORRIDOR }
                SettingDivider()
                SettingSwitch(stringResource(R.string.set_trip_reminders), stringResource(R.string.set_trip_reminders_hint), s.tripReminders) { v -> nav.updateSettings { it.copy(tripReminders = v) } }
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

            SettingsSection(stringResource(R.string.sec_power)) {
                SettingRow(stringResource(R.string.set_power), stringResource(R.string.set_power_hint), value = stringResource(powerLabel(s.powerSaver))) { dialog = Dlg.POWER }
                SettingDivider()
                SettingRow(stringResource(R.string.set_storage), stringResource(R.string.set_storage_hint)) { dialog = Dlg.STORAGE }
            }

            SettingsSection(stringResource(R.string.sec_server)) {
                SettingRow(stringResource(R.string.set_server), stringResource(if (ServerConfig.token.isBlank()) R.string.set_server_hint_missing else R.string.set_server_hint_ok)) { dialog = Dlg.SERVER }
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
                SettingRow(stringResource(R.string.set_export), stringResource(R.string.set_export_hint)) {
                    val zip = DataExport.build(ctx, state.diary)
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", zip)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(android.content.Intent.createChooser(send, ctx.getString(R.string.set_export)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
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
        Dlg.CAMERAS -> {
            val banned = stringResource(R.string.cameras_status_banned)
            val restricted = stringResource(R.string.cameras_status_restricted)
            val unclear = stringResource(R.string.cameras_status_unclear)
            val rules = app.kiloquest.nav.CameraPolicy.rules.joinToString("\n") { r ->
                java.util.Locale("", r.country).getDisplayCountry(java.util.Locale.getDefault()) + ": " + when (r.status) {
                    app.kiloquest.nav.LegalStatus.BANNED -> banned
                    app.kiloquest.nav.LegalStatus.RESTRICTED -> restricted
                    app.kiloquest.nav.LegalStatus.UNCLEAR -> unclear
                }
            }
            InfoDialog(stringResource(R.string.set_cameras), stringResource(R.string.cameras_info) + "\n\n" + rules, close) { dialog = Dlg.NONE }
        }
        Dlg.VOICE_HELP -> {
            val st = voiceStatus
            val msg = when (st) {
                VoiceAnswers.Status.OLD_ANDROID -> R.string.voice_help_old
                VoiceAnswers.Status.NO_ON_DEVICE -> R.string.voice_help_pack
                VoiceAnswers.Status.NO_MIC -> R.string.voice_help_mic
                VoiceAnswers.Status.OK -> R.string.voice_help_ok
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = Dlg.NONE },
                title = { Text(stringResource(R.string.set_voice_commands)) },
                text = { Text(stringResource(msg), style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    if (st == VoiceAnswers.Status.NO_ON_DEVICE || st == VoiceAnswers.Status.NO_MIC) {
                        androidx.compose.material3.TextButton(onClick = {
                            dialog = Dlg.NONE
                            val i = if (st == VoiceAnswers.Status.NO_MIC) android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))
                            else android.content.Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                            runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text(stringResource(if (st == VoiceAnswers.Status.NO_MIC) R.string.voice_help_open_app else R.string.voice_help_open_voice)) }
                    }
                },
                dismissButton = {
                    Row {
                        if (st == VoiceAnswers.Status.NO_ON_DEVICE || st == VoiceAnswers.Status.OLD_ANDROID) {
                            androidx.compose.material3.TextButton(onClick = { dialog = Dlg.CLOUD_WARN }) { Text(stringResource(R.string.voice_help_cloud)) }
                        }
                        androidx.compose.material3.TextButton(onClick = { dialog = Dlg.NONE }) { Text(close) }
                    }
                },
            )
        }
        Dlg.APP_ICON -> {
            var chosen by rememberSaveable { mutableStateOf(AppIcon.current(ctx).name) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = Dlg.NONE },
                title = { Text(stringResource(R.string.set_app_icon)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.app_icon_info), style = MaterialTheme.typography.bodySmall)
                        AppIcon.Variant.entries.forEach { v ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { chosen = v.name },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                androidx.compose.material3.RadioButton(selected = chosen == v.name, onClick = null)
                                androidx.compose.foundation.Image(painterResource(v.previewRes), contentDescription = null, modifier = Modifier.size(48.dp))
                                Text(stringResource(v.labelRes), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { AppIcon.apply(ctx, AppIcon.Variant.valueOf(chosen)); dialog = Dlg.NONE }) { Text(stringResource(R.string.dlg_save)) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { dialog = Dlg.NONE }) { Text(cancel) } },
            )
        }
        Dlg.CLOUD_WARN -> androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = Dlg.NONE },
            title = { Text(stringResource(R.string.cloud_warn_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(stringResource(R.string.cloud_warn_text), style = MaterialTheme.typography.bodyMedium) } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { nav.updateSettings { it.copy(cloudSpeech = true) }; dialog = Dlg.NONE }) { Text(stringResource(R.string.cloud_warn_allow), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { dialog = Dlg.NONE }) { Text(cancel) } },
        )
        Dlg.POWER -> ChoiceDialog(
            stringResource(R.string.set_power),
            app.kiloquest.nav.PowerSaverMode.entries.map { Triple(it, stringResource(powerLabel(it)), stringResource(powerHint(it))) },
            s.powerSaver, { v -> nav.updateSettings { it.copy(powerSaver = v) } }, { dialog = Dlg.NONE }, close,
        )
        Dlg.STORAGE -> {
            var usage by rememberSaveable { mutableStateOf(0) }
            val u = remember(usage) { StorageScanner.measure(ctx) }
            val fmt = app.kiloquest.nav.StorageUsage.Companion::format
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = Dlg.NONE },
                title = { Text(stringResource(R.string.set_storage)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.storage_photos, fmt(u.photosBytes)))
                        Text(stringResource(R.string.storage_offline, fmt(u.offlineBytes)))
                        Text(stringResource(R.string.storage_cache, fmt(u.cacheBytes)))
                        if (u.warn) Text(stringResource(R.string.storage_warn), color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { StorageScanner.clearCache(ctx); usage++ }) { Text(stringResource(R.string.storage_clear_cache)) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { dialog = Dlg.NONE }) { Text(close) } },
            )
        }
        Dlg.SERVER -> {
            val prefs = ctx.getSharedPreferences("kq", android.content.Context.MODE_PRIVATE)
            var url by rememberSaveable { mutableStateOf(ServerConfig.url) }
            var token by rememberSaveable { mutableStateOf("") }
            var bad by rememberSaveable { mutableStateOf(false) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dialog = Dlg.NONE },
                title = { Text(stringResource(R.string.set_server)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.set_server_info), style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.OutlinedTextField(url, { url = it; bad = false }, singleLine = true, label = { Text(stringResource(R.string.set_server_url)) }, isError = bad, supportingText = { if (bad) Text(stringResource(R.string.set_server_https)) })
                        androidx.compose.material3.OutlinedTextField(
                            token, { token = it }, singleLine = true, label = { Text(stringResource(R.string.set_server_token)) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        // leeres Tokenfeld = bisherigen Token behalten
                        if (ServerConfig.save(prefs, url, token.ifBlank { ServerConfig.token })) dialog = Dlg.NONE else bad = true
                    }) { Text(stringResource(R.string.dlg_save)) }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { dialog = Dlg.NONE }) { Text(cancel) } },
            )
        }
        Dlg.PRIVACY -> InfoDialog(stringResource(R.string.set_privacy), stringResource(R.string.privacy_text), close) { dialog = Dlg.NONE }
        Dlg.LICENSES -> InfoDialog(stringResource(R.string.set_licenses), stringResource(R.string.licenses_text), close) { dialog = Dlg.NONE }
    }
}

private fun powerLabel(m: app.kiloquest.nav.PowerSaverMode) = when (m) {
    app.kiloquest.nav.PowerSaverMode.AUTO -> R.string.power_auto
    app.kiloquest.nav.PowerSaverMode.ON -> R.string.power_on
    app.kiloquest.nav.PowerSaverMode.OFF -> R.string.power_off
}

private fun powerHint(m: app.kiloquest.nav.PowerSaverMode) = when (m) {
    app.kiloquest.nav.PowerSaverMode.AUTO -> R.string.power_auto_hint
    app.kiloquest.nav.PowerSaverMode.ON -> R.string.power_on_hint
    app.kiloquest.nav.PowerSaverMode.OFF -> R.string.power_off_hint
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
