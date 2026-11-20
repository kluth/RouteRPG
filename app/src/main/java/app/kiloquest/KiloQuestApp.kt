package app.kiloquest

import android.app.Application

class KiloQuestApp : Application() {
    val nav: NavController by lazy { NavController(this) }
}
