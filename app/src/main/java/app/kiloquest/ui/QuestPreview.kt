package app.kiloquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kiloquest.R
import app.kiloquest.game.LanguageCourses
import app.kiloquest.game.NatureSpotting
import app.kiloquest.game.PlaneSpotting
import app.kiloquest.game.Quest
import app.kiloquest.game.QuestCatalog
import app.kiloquest.game.QuestType
import app.kiloquest.game.RouteFacts
import app.kiloquest.game.WikiStories
import app.kiloquest.stringByKey

private val PENDING = setOf(
    RouteFacts.PENDING_KEY, WikiStories.PENDING_KEY, NatureSpotting.PENDING_KEY, PlaneSpotting.PENDING_KEY, LanguageCourses.PENDING_KEY,
)

private fun emoji(t: QuestType) = when (t) {
    QuestType.SPOTTING -> "👀"
    QuestType.ROUTE_FACT -> "📍"
    QuestType.ALPHABET -> "🔤"
    QuestType.STORY -> "📖"
    QuestType.BREAK -> "🤸"
    QuestType.QUIZ -> "❓"
    QuestType.NATURE -> "🦌"
    QuestType.LANGUAGE -> "🗣️"
    QuestType.SKY -> "✈️"
}

@Composable
private fun typeName(t: QuestType): String = stringResource(
    when (t) {
        QuestType.SPOTTING -> R.string.qtype_spotting
        QuestType.ROUTE_FACT -> R.string.qtype_route_fact
        QuestType.ALPHABET -> R.string.qtype_alphabet
        QuestType.STORY -> R.string.qtype_story
        QuestType.BREAK -> R.string.qtype_break
        QuestType.QUIZ -> R.string.qtype_quiz
        QuestType.NATURE -> R.string.qtype_nature
        QuestType.LANGUAGE -> R.string.qtype_language
        QuestType.SKY -> R.string.qtype_sky
    },
)

/** Vorschau der anstehenden Aufgaben: feste Aufgaben im Wortlaut, Platzhalter als Überraschung, vorgeladene als "Bereit". */
@Composable
fun QuestPreview(plan: List<Quest>, questOffsetMeters: Double = 0.0, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleMedium)
        if (plan.isEmpty()) {
            Text(stringResource(R.string.preview_empty), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(stringResource(R.string.preview_summary, plan.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val shown = if (expanded) plan else plan.take(8)
        for (q in shown) {
            val live = q.textKey in PENDING
            val dynamic = live || QuestCatalog.default.firstOrNull { it.id == q.templateId }?.textKey in PENDING
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Text(questEmoji(q), style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(typeName(q.type), style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.preview_km, ((q.triggerMeters - questOffsetMeters) / 1000).toInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (live) {
                            Text(stringResource(R.string.preview_live), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(ctx.stringByKey(q.textKey, q.args), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (dynamic) Text(stringResource(R.string.preview_ready), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
        if (plan.size > 8) TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.preview_less else R.string.preview_more))
        }
    }
}
