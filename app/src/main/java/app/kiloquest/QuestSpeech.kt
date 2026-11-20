package app.kiloquest

import android.content.Context
import app.kiloquest.game.Quest
import app.kiloquest.game.QuestKind

/** Form und Farbe der Antwort Nr. [i] (wie auf den Knoepfen der Mini-Ansicht), damit Kinder ohne Lesen waehlen koennen. */
fun Context.optionShapeName(i: Int): String = getString(
    when (i % 3) {
        0 -> R.string.shape_circle_blue
        1 -> R.string.shape_square_orange
        else -> R.string.shape_triangle_green
    },
)

/** "Blauer Kreis: Rentier. Oranges Quadrat: Kamel. ..." fuer Quizfragen, sonst leer. */
fun Context.optionsSpeech(q: Quest): String =
    if (q.kind != QuestKind.CHOICE) "" else q.optionKeys.mapIndexed { i, key -> optionShapeName(i) + ": " + stringByKey(key) }.joinToString(". ")

/** Alles, was Kilo vorliest: die Aufgabe und, bei Quizfragen fuer die Kleinsten, die Antworten mit Form und Farbe. */
fun Context.questSpeech(q: Quest, withOptions: Boolean): String {
    val text = stringByKey(q.textKey, q.args)
    val options = if (withOptions) optionsSpeech(q) else ""
    return if (options.isEmpty()) text else "$text $options"
}
