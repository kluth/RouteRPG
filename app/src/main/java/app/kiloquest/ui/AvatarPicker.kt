package app.kiloquest.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kiloquest.R
import app.kiloquest.game.AvatarCategory
import app.kiloquest.game.Avatars

@Composable
private fun categoryName(c: AvatarCategory) = stringResource(
    when (c) {
        AvatarCategory.VEHICLES -> R.string.avatar_cat_vehicles
        AvatarCategory.AIR_SEA_SPACE -> R.string.avatar_cat_air
        AvatarCategory.ANIMALS -> R.string.avatar_cat_animals
        AvatarCategory.HEROES -> R.string.avatar_cat_heroes
        AvatarCategory.FUN -> R.string.avatar_cat_fun
    },
)

/**
 * Figur fuer die eigene Position waehlen: Fahrzeuge, Flugzeuge, Tiere, Helden ... oder ein eigenes Emoji.
 * Die Auswahl gilt sofort (Karte und Hauptkarte) und bleibt auf dem Geraet gespeichert.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var category by remember { mutableStateOf(Avatars.all.firstOrNull { it.emoji == current }?.category ?: AvatarCategory.VEHICLES) }
    var custom by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.screenColumn().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = Modifier.size(72.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(current, style = TextStyle(fontSize = 44.sp)) }
                }
                Text(stringResource(R.string.avatar_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in AvatarCategory.entries) {
                    FilterChip(selected = c == category, onClick = { category = c }, label = { Text(categoryName(c)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in Avatars.byCategory(category)) {
                    Surface(
                        onClick = { onPick(a.emoji) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (a.emoji == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(64.dp),
                    ) { Box(contentAlignment = Alignment.Center) { Text(a.emoji, style = TextStyle(fontSize = 34.sp)) } }
                }
            }
            // Eigenes Emoji: alles, was die Emoji-Tastatur anbietet
            Text(stringResource(R.string.avatar_custom), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it; invalid = false },
                    placeholder = { Text(stringResource(R.string.avatar_custom_hint)) },
                    singleLine = true, isError = invalid,
                    shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val ok = Avatars.sanitizeCustom(custom)
                        if (ok == null) invalid = true else { onPick(ok); custom = "" }
                    },
                    modifier = Modifier.heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.avatar_apply)) }
            }
            if (invalid) Text(stringResource(R.string.avatar_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.avatar_done)) }
        }
    }
}
