package app.kiloquest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.kiloquest.ui.screenColumn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.game.AgeBand
import app.kiloquest.game.QuestKind
import app.kiloquest.game.StickerBook

/**
 * Kinderansicht (Handy/Tablet, nicht im Auto-Display): aktuelle Aufgabe, Punkte, Sticker.
 * Grosse Schrift und grosse Buttons; der Fahrer bekommt nur die Sprachansage.
 */
@Composable
fun KidsScreen(state: UiState, nav: KidsActions) {
    // Karte nur auf Wunsch im Vollbild, sonst klein; Figur-Wahl liegt darueber
    var mapFull by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var picker by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        KidsContent(state, nav, { mapFull = true }, { picker = true })
        if (mapFull) app.kiloquest.ui.KidMapFull(state, { mapFull = false }, { picker = true }, nav::setRealMap)
        if (picker) app.kiloquest.ui.AvatarPicker(state.avatar, nav::setAvatar, { picker = false })
    }
}

@Composable
private fun KidsContent(state: UiState, nav: KidsActions, onOpenMap: () -> Unit, onPickAvatar: () -> Unit) {
    app.kiloquest.ui.kids.KidsHome(state, nav, onOpenMap, onPickAvatar)
}

@Composable
fun AgePicker(selected: AgeBand, onSelect: (AgeBand) -> Unit) {
    Text(stringResource(R.string.kids_age), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (band in AgeBand.entries) {
            FilterChip(
                selected = band == selected,
                onClick = { onSelect(band) },
                label = {
                    Text(
                        stringResource(
                            when (band) {
                                AgeBand.MINI -> R.string.age_mini
                                AgeBand.EXPLORER -> R.string.age_explorer
                                AgeBand.PRO -> R.string.age_pro
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
fun PlayerPicker(selected: Int, onSelect: (Int) -> Unit) {
    Text(stringResource(R.string.kids_players), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in 1..4) {
            FilterChip(selected = n == selected, onClick = { onSelect(n) }, label = { Text(n.toString()) })
        }
    }
}
