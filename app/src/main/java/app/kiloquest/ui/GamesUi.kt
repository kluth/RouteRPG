package app.kiloquest.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kiloquest.NavController
import app.kiloquest.R
import app.kiloquest.UiState
import app.kiloquest.game.AudioGameType
import app.kiloquest.game.KiloSeries
import app.kiloquest.game.TeamGoal

/** Gemeinsames Teamziel der Familie: ein Fortschrittsbalken statt Rangliste. */
@Composable
fun TeamGoalBar(goal: TeamGoal, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.team_goal_title) + ": " + stringResource(R.string.team_goal_progress, goal.collected.coerceAtMost(goal.target), goal.target)
    Column(modifier.semantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { goal.progress.toFloat() }, modifier = Modifier.fillMaxWidth().heightIn(min = 10.dp))
    }
}

