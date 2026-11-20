package app.kiloquest.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.NavController

import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.stringByKey

/** Notfall-Karte: Nummer, Standort (teilbar), naechste Krankenhaeuser. Waehlt nie selbst, oeffnet nur das Telefon. */
@Composable
fun EmergencyDialog(state: UiState, nav: NavController) {
    val e = state.emergency ?: return
    EmergencyView(
        app.kiloquest.link.EmergencyDto(e.number, e.location, e.hospitals.map { app.kiloquest.link.HospitalDto(it.name, Math.round(it.distanceMeters / 1000.0).toInt()) }, e.loading, state.country),
        nav::closeEmergency,
    )
}

/** Dieselbe Karte fuer Hauptgeraet und Beifahrer-Geraet (Daten + Schliessen). */
@Composable
fun EmergencyView(e: app.kiloquest.link.EmergencyDto, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val phrases = remember(e.country) { app.kiloquest.game.ParentPhrases.forCountry(e.country)?.toList().orEmpty() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.emergency_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.emergency_number, e.number), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.emergency_location), style = MaterialTheme.typography.titleSmall)
                Text(e.location, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.emergency_hospitals), style = MaterialTheme.typography.titleSmall)
                when {
                    e.loading -> Text(stringResource(R.string.emergency_searching))
                    e.hospitals.isEmpty() -> Text(stringResource(R.string.emergency_none))
                    else -> e.hospitals.forEach { Text(stringResource(R.string.emergency_hospital_line, it.name, it.km)) }
                }
                Text(stringResource(R.string.emergency_disclaimer, e.number), style = MaterialTheme.typography.bodySmall)
                if (phrases.isNotEmpty()) {
                    Text(stringResource(R.string.parentphrase_title), style = MaterialTheme.typography.titleSmall)
                    phrases.forEach { (ph, text) ->
                        Text(ctx.stringByKey(ph.textKey), style = MaterialTheme.typography.bodySmall)
                        Text(text, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(R.string.parentphrase_note), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.emergency_satellite), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + e.number)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.emergency_dial, e.number)) }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, e.location)
                        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.emergency_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.emergency_share)) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.dlg_close)) } },
    )
}
