package app.kiloquest

/** Was die Kinderansicht ausloesen darf: lokal (NavController) oder ueber ein gekoppeltes Geraet. */
interface KidsActions {
    fun answerQuest(choice: Int? = null)
    fun skipQuest()
    fun dismissFeedback()
    fun showKids(show: Boolean)
    /** "Ich muss mal!": fragt den Beifahrer, loest den Klo-Alarm nicht selbst aus. */
    fun requestToilet()
}
