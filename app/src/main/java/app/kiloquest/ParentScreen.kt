package app.kiloquest

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Beifahrer-Menue: hier entscheidet der Beifahrer ueber Umfahrung und Pause und stellt die App ein.
 * Der Fahrer hoert dazu nur eine kurze Ansage. Erreichbar per langem Druck auf den Quests-Knopf.
 */
@Composable
fun ParentScreen(state: UiState, nav: NavController) {
    val ctx = LocalContext.current
    BackHandler { nav.showParent(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) nav.setVoiceMode(true)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.parent_title), style = MaterialTheme.typography.headlineMedium)

            val p = state.proposal
            if (p != null) {
                Card(MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        stringResource(if (p.blocked) R.string.reroute_blocked else R.string.reroute_title) +
                            if (p.savedMinutes > 0) " · " + stringResource(R.string.reroute_saves, p.savedMinutes) else "",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(stringResource(R.string.parent_driver_hears), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = nav::acceptReroute, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.reroute_accept)) }
                        OutlinedButton(onClick = nav::dismissReroute, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.reroute_dismiss)) }
                    }
                }
            }
            val bp = state.breakProposal
            if (bp != null) {
                Card(MaterialTheme.colorScheme.secondaryContainer) {
                    Text(stringResource(R.string.break_banner, formatKm(bp.aheadMeters), nav.stopText(bp.stop)), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = nav::acceptBreak, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.break_accept)) }
                        OutlinedButton(onClick = nav::laterBreak, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.break_later)) }
                    }
                }
            }

            Card(MaterialTheme.colorScheme.surface) {
                AgePicker(state.ageBand, nav::setAgeBand)
                PlayerPicker(state.players, nav::setPlayers)
            }

            Card(MaterialTheme.colorScheme.surface) {
                SwitchRow(R.string.kids_sickness_mode, null, state.sicknessMode, true, nav::setSicknessMode)
                SwitchRow(
                    R.string.voice_mode,
                    if (state.voiceAvailable) null else R.string.voice_unavailable,
                    state.voiceMode,
                    state.voiceAvailable,
                ) { on ->
                    if (!on) nav.setVoiceMode(false)
                    else if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) nav.setVoiceMode(true)
                    else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                }
                SwitchRow(R.string.car_simple_map, R.string.car_simple_map_hint, state.carSimpleMap, true, nav::setCarSimpleMap)
            }

            app.kiloquest.link.LinkHostCard()
            // Altersstufe dieses Geraets (gilt als Kinder-Geraet) und Verbindung zu einem Hauptgeraet
            app.kiloquest.link.LinkClientCard(state.ageBand)

            Card(MaterialTheme.colorScheme.surface) {
                if (state.route != null && !state.navigating) PackageLine(state.pkg, nav)
                OutlinedButton(onClick = { nav.showParent(false); nav.showRiddles(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.riddle_open))
                }
                OutlinedButton(onClick = nav::clearOfflineMaps, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pkg_clear), color = MaterialTheme.colorScheme.error)
                }
            }

            Text(stringResource(R.string.parent_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { nav.showParent(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.kids_back))
            }
        }
    }
}

@Composable
private fun Card(color: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = color, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(title: Int, subtitle: Int?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun formatKm(meters: Double): String =
    if (meters >= 1000) stringResource(R.string.unit_km, meters / 1000.0) else stringResource(R.string.unit_m, meters.toInt())
