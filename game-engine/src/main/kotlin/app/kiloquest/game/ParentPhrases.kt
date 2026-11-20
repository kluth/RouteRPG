package app.kiloquest.game

/** Saetze fuer Eltern in Notsituationen. Die Bedeutung steht in den String-Ressourcen (`parentphrase_<id>`), der Satz im Land als Daten. */
enum class ParentPhrase(val id: String) {
    DOCTOR("doctor"), PHARMACY("pharmacy"), ALLERGY("allergy"), CHILD_ILL("child_ill"), HELP("help"), HOSPITAL("hospital");

    val textKey: String get() = "parentphrase_$id"
}

/**
 * Notfall-Saetze je Land (ISO-Code) in der Landessprache, in der Reihenfolge der [ParentPhrase]-Eintraege.
 * **Von Muttersprachlern pruefen lassen**, bevor man sich im Ernstfall darauf verlaesst (Hinweis steht in der App).
 */
object ParentPhrases {
    private fun p(country: String, vararg w: String) = country to ParentPhrase.entries.zip(w.toList()).toMap()

    // Reihenfolge: doctor, pharmacy, allergy, child_ill, help, hospital
    val all: Map<String, Map<ParentPhrase, String>> = mapOf(
        p("SE", "Jag behöver en läkare", "Var finns närmaste apotek?", "Mitt barn har en allergi", "Mitt barn är sjukt", "Snälla, hjälp mig", "Var finns närmaste sjukhus?"),
        p("DK", "Jeg har brug for en læge", "Hvor er nærmeste apotek?", "Mit barn har en allergi", "Mit barn er sygt", "Hjælp mig, tak", "Hvor er nærmeste hospital?"),
        p("FI", "Tarvitsen lääkärin", "Missä on lähin apteekki?", "Lapsellani on allergia", "Lapseni on sairas", "Auttakaa minua, kiitos", "Missä on lähin sairaala?"),
        p("NO", "Jeg trenger en lege", "Hvor er nærmeste apotek?", "Barnet mitt har en allergi", "Barnet mitt er sykt", "Vær så snill, hjelp meg", "Hvor er nærmeste sykehus?"),
        p("NL", "Ik heb een dokter nodig", "Waar is de dichtstbijzijnde apotheek?", "Mijn kind heeft een allergie", "Mijn kind is ziek", "Help me alstublieft", "Waar is het dichtstbijzijnde ziekenhuis?"),
        p("PL", "Potrzebuję lekarza", "Gdzie jest najbliższa apteka?", "Moje dziecko ma alergię", "Moje dziecko jest chore", "Proszę mi pomóc", "Gdzie jest najbliższy szpital?"),
        p("FR", "J'ai besoin d'un médecin", "Où est la pharmacie la plus proche ?", "Mon enfant a une allergie", "Mon enfant est malade", "Aidez-moi, s'il vous plaît", "Où est l'hôpital le plus proche ?"),
        p("IT", "Ho bisogno di un medico", "Dov'è la farmacia più vicina?", "Mio figlio ha un'allergia", "Mio figlio è malato", "Aiutatemi, per favore", "Dov'è l'ospedale più vicino?"),
        p("ES", "Necesito un médico", "¿Dónde está la farmacia más cercana?", "Mi hijo tiene una alergia", "Mi hijo está enfermo", "Ayúdeme, por favor", "¿Dónde está el hospital más cercano?"),
        p("HR", "Trebam liječnika", "Gdje je najbliža ljekarna?", "Moje dijete ima alergiju", "Moje dijete je bolesno", "Pomozite mi, molim", "Gdje je najbliža bolnica?"),
        p("CZ", "Potřebuji lékaře", "Kde je nejbližší lékárna?", "Moje dítě má alergii", "Moje dítě je nemocné", "Pomozte mi, prosím", "Kde je nejbližší nemocnice?"),
    )

    fun forCountry(code: String?): Map<ParentPhrase, String>? = all[code?.uppercase()]
}
