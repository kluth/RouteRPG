package app.kiloquest

/** Was die Kinderansicht ausloesen darf: lokal (NavController) oder ueber ein gekoppeltes Geraet. */
interface KidsActions {
    fun answerQuest(choice: Int? = null)
    fun skipQuest()
    fun dismissFeedback()
    fun showKids(show: Boolean)
}
