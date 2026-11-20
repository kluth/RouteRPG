package app.kiloquest

/** Was die Kinderansicht ausloesen darf: lokal (NavController) oder ueber ein gekoppeltes Geraet. */
interface KidsActions {
    fun answerQuest(choice: Int? = null)
    fun skipQuest()
    fun dismissFeedback()
    fun showKids(show: Boolean)
    /** "Ich muss mal!": fragt den Beifahrer, loest den Klo-Alarm nicht selbst aus. */
    fun requestToilet()
    /** Figur fuer die eigene Position (Emoji); gilt nur auf diesem Geraet. */
    fun setAvatar(emoji: String)
    /** Kinderkarte: echte Kacheln (true) oder schematisch (false); Einstellung dieses Geraets. */
    fun setRealMap(on: Boolean)
    /** Spielt eine Kilo-Aufnahme zum Text ab; false, wenn es keine gibt (dann liest die Sprachausgabe des Geraets vor). */
    fun speakAloud(text: String): Boolean = false
}
