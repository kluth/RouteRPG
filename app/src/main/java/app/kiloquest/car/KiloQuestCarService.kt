package app.kiloquest.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class KiloQuestCarService : CarAppService() {
    // Privater Gebrauch/Sideload: alle Hosts erlaubt. Fuer eine Veroeffentlichung durch die
    // Google-Allowlist (hosts_allowlist) ersetzen.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = KiloQuestSession()
}
