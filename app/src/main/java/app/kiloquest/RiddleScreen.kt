package app.kiloquest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.nav.Riddle

/** Eltern legen eigene Raetsel an (lokal gespeichert); sie erscheinen unterwegs als Aufgabe fuer die Kinder. */
@Composable
fun RiddleScreen(state: UiState, nav: NavController) {
    val ctx = LocalContext.current
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateOf(listOf("", "", "")) }
    var correct by remember { mutableStateOf(0) }
    var km by remember { mutableStateOf("") }
    var place by remember { mutableStateOf<Place?>(null) }
    var placeQuery by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(emptyList<String>()) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.riddle_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.riddle_hint))

            if (state.riddles.isEmpty()) Text(stringResource(R.string.riddle_none))
            state.riddles.forEach { r ->
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.question, style = MaterialTheme.typography.titleSmall)
                        val trigger = r.atKm?.let { stringResource(R.string.riddle_trigger_km, it.toString()) }
                            ?: r.nearName?.let { stringResource(R.string.riddle_trigger_place, it) }.orEmpty()
                        Text(trigger, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { nav.deleteRiddle(r.id) }) { Text(stringResource(R.string.riddle_delete)) }
                    }
                }
            }

            OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text(stringResource(R.string.riddle_question)) }, modifier = Modifier.fillMaxWidth())
            for (i in 0..2) {
                OutlinedTextField(
                    value = options.value[i],
                    onValueChange = { v -> options.value = options.value.toMutableList().also { it[i] = v } },
                    label = { Text(stringResource(R.string.riddle_option, i + 1)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(stringResource(R.string.riddle_correct), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..2) FilterChip(selected = correct == i, onClick = { correct = i }, label = { Text("${i + 1}") })
            }
            OutlinedTextField(value = km, onValueChange = { km = it }, label = { Text(stringResource(R.string.riddle_at_km)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.riddle_place_hint), style = MaterialTheme.typography.bodySmall)
            place?.let { Text(stringResource(R.string.riddle_trigger_place, it.name), color = MaterialTheme.colorScheme.primary) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = placeQuery, onValueChange = { placeQuery = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text(stringResource(R.string.search_hint)) })
                Button(onClick = { nav.search(placeQuery) }, modifier = Modifier.padding(start = 8.dp), enabled = !state.busy) { Text(stringResource(R.string.search_action)) }
            }
            state.results.forEach { p ->
                Text(p.name, modifier = Modifier.fillMaxWidth().clickable { place = p; km = ""; nav.clearResults() }.padding(vertical = 8.dp))
            }

            errors.forEach { key ->
                @Suppress("DiscouragedApi")
                val id = ctx.resources.getIdentifier("riddle_err_$key", "string", ctx.packageName)
                if (id != 0) Text(stringResource(id), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    val kmValue = km.trim().replace(',', '.').toDoubleOrNull()
                    val r = Riddle(
                        id = java.util.UUID.randomUUID().toString(), question = question.trim(),
                        options = options.value.map { it.trim() }, correctIndex = correct,
                        atKm = if (place == null) kmValue else null,
                        near = place?.position, nearName = place?.name,
                    )
                    errors = nav.addRiddle(r)
                    if (errors.isEmpty()) {
                        question = ""; options.value = listOf("", "", ""); correct = 0; km = ""; place = null; placeQuery = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.riddle_save)) }
            OutlinedButton(onClick = { nav.showRiddles(false); nav.clearResults() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.kids_back))
            }
        }
    }
}
