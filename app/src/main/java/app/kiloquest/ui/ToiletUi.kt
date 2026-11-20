package app.kiloquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kiloquest.R
import app.kiloquest.ToiletPhase
import app.kiloquest.ToiletState

/** Grosser Klo-Alarm-Knopf: ein Tipp, kein Nachfragen (Kinder brauchen es manchmal schnell). */
@Composable
fun ToiletAlarmButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 18.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_wc), contentDescription = null, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.toilet_alarm), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun minutesText(seconds: Double?): String =
    if (seconds == null) "" else stringResource(R.string.unit_min, (seconds / 60).toInt().coerceAtLeast(1))

@Composable
private fun distText(meters: Double?): String = when {
    meters == null -> ""
    meters >= 1000 -> stringResource(R.string.unit_km, meters / 1000.0)
    else -> stringResource(R.string.unit_m, meters.toInt())
}

/** Stand des Klo-Alarms; [onContinue] beendet den Halt bzw. bricht ab (zurueck zum alten Ziel). Null-Zustand zeichnet nichts. */
@Composable
fun ToiletStatusCard(state: ToiletState?, onContinue: () -> Unit, modifier: Modifier = Modifier, showActions: Boolean = true) {
    if (state == null) return
    KiloCard(modifier, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_wc), contentDescription = null, modifier = Modifier.size(28.dp))
            Text(
                when (state.phase) {
                    ToiletPhase.SEARCHING -> stringResource(R.string.toilet_status_searching)
                    ToiletPhase.HEADING -> stringResource(R.string.toilet_status_heading, state.label, minutesText(state.seconds), distText(state.meters))
                    ToiletPhase.ARRIVED -> stringResource(R.string.toilet_status_arrived, state.label)
                    ToiletPhase.NONE -> stringResource(R.string.toilet_status_none)
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (showActions && state.phase != ToiletPhase.NONE) {
            if (state.phase == ToiletPhase.ARRIVED) {
                Button(onClick = onContinue, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.toilet_continue)) }
            } else {
                OutlinedButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.toilet_cancel), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

/** Ein Kind hat "Ich muss mal" gedrueckt: Beifahrer entscheidet. */
@Composable
fun ToiletRequestCard(name: String?, onStart: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (name == null) return
    KiloCard(modifier, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_wc), contentDescription = null, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.toilet_request_title, name), style = MaterialTheme.typography.titleSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStart, modifier = Modifier.weight(1f).heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                Text(stringResource(R.string.toilet_start), maxLines = 2, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Text(stringResource(R.string.toilet_not_now), color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 2, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** "Ich muss mal!" fuer Kinder: fragt nur an. */
@Composable
fun ToiletKidButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(20.dp)) {
        Icon(painterResource(R.drawable.ic_wc), contentDescription = null, modifier = Modifier.size(24.dp))
        Text(stringResource(R.string.toilet_kid_button), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
    }
}
