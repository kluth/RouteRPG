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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.nav.TollGuide
import app.kiloquest.stringByKey

/** Maut/Vignetten der Route und lange Strecken ohne Ortschaften (Hinweis auf moegliche Funklocher). */
@Composable
fun TripInfoCard(state: UiState) {
    if (state.tolls.isEmpty() && state.remoteStretches.isEmpty()) return
    val ctx = LocalContext.current
    KiloCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.tolls.isNotEmpty()) {
                Text(stringResource(R.string.toll_title), style = MaterialTheme.typography.titleMedium)
                state.tolls.forEach { r ->
                    Text(ctx.stringByKey(TollGuide.textKey(r.country)), style = MaterialTheme.typography.bodyMedium)
                    r.url?.let { url ->
                        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.toll_open) + " (" + r.country + ")")
                        }
                    }
                }
                Text(stringResource(R.string.toll_asof, TollGuide.AS_OF), style = MaterialTheme.typography.bodySmall)
            }
            if (state.remoteStretches.isNotEmpty()) {
                Text(stringResource(R.string.remote_title), style = MaterialTheme.typography.titleMedium)
                state.remoteStretches.take(6).forEach {
                    Text(stringResource(R.string.remote_item, (it.startMeters / 1000).toInt(), (it.endMeters / 1000).toInt(), (it.lengthMeters / 1000).toInt()), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.remote_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Notfall-Karte: Nummer, Standort (teilbar), naechste Krankenhaeuser. Waehlt nie selbst, oeffnet nur das Telefon. */
@Composable
fun EmergencyDialog(state: UiState, nav: NavController) {
    val e = state.emergency ?: return
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = nav::closeEmergency,
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
                    else -> e.hospitals.forEach { Text(stringResource(R.string.emergency_hospital_line, it.name, Math.round(it.distanceMeters / 1000.0).toInt())) }
                }
                Text(stringResource(R.string.emergency_disclaimer, e.number), style = MaterialTheme.typography.bodySmall)
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
        confirmButton = { TextButton(onClick = nav::closeEmergency) { Text(stringResource(R.string.dlg_close)) } },
    )
}
