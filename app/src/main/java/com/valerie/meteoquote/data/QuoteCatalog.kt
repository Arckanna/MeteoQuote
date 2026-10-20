package com.valerie.meteoquote.data

import com.valerie.meteoquote.data.model.Quote

/** IDs are persisted: keep existing IDs stable when expanding the collection. */
internal object QuoteCatalog {
    data class Entry(val id: String, val quote: Quote) {
        val formatted: String get() = "\"${quote.text}\" — ${quote.author}"
    }

    fun category(code: Int?): String = when (code) {
        0 -> "clear"
        1, 2, 3 -> "clouds"
        45, 48 -> "fog"
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> "rain"
        71, 73, 75, 77, 85, 86 -> "snow"
        95, 96, 99 -> "thunder"
        else -> "neutral"
    }

    fun find(id: String?): Entry? = listOf(null, 0, 3, 45, 61, 71, 95)
        .flatMap(::entries).firstOrNull { it.id == id }

    fun entries(code: Int?): List<Entry> {
        val clear = listOf(
            Entry("clear-1", Quote("Le soleil brille pour tout le monde.", "Proverbe")),
            Entry("clear-2", Quote("La simplicité est la sophistication suprême.", "L. de Vinci")),
            Entry("clear-3", Quote("Crée la lumière que tu cherches.", "Anonyme"))
        )
        val clouds = listOf(
            Entry("clouds-1", Quote("Au-dessus des nuages, le ciel est toujours bleu.", "Proverbe")),
            Entry("clouds-2", Quote("Patience : les nuages se dissipent toujours.", "Anonyme")),
            Entry("clouds-3", Quote("Notre clarté naît parfois de l'ombre.", "Anonyme"))
        )
        val rain = listOf(
            Entry("rain-1", Quote("Sans la pluie, rien ne pousse.", "Anonyme")),
            Entry("rain-2", Quote("Il faut de la pluie pour voir l'arc-en-ciel.", "D. Parton")),
            Entry("rain-3", Quote("Chaque goutte prépare une moisson.", "Proverbe"))
        )
        val snow = listOf(
            Entry("snow-1", Quote("La paix tombe parfois comme la neige.", "Anonyme")),
            Entry("snow-2", Quote("Chaque flocon a sa forme et son destin.", "Proverbe")),
            Entry("snow-3", Quote("Le silence de la neige dit l'essentiel.", "Anonyme"))
        )
        val fog = listOf(
            Entry("fog-1", Quote("Quand la route est brumeuse, avance pas à pas.", "Anonyme")),
            Entry("fog-2", Quote("La clarté se révèle en chemin.", "Anonyme")),
            Entry("fog-3", Quote("Tout brouillard finit par se lever.", "Proverbe"))
        )
        val thunder = listOf(
            Entry("thunder-1", Quote("Le courage n'est pas l'absence de peur.", "N. Mandela")),
            Entry("thunder-2", Quote("L'éclair éclaire l'instant : saisis-le.", "Anonyme")),
            Entry("thunder-3", Quote("La tempête forge les marins.", "Proverbe"))
        )
        return when (code) {
            0 -> clear
            1, 2, 3 -> clouds
            45, 48 -> fog
            51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> rain
            71, 73, 75, 77, 85, 86 -> snow
            95, 96, 99 -> thunder
            else -> listOf(
                Entry("neutral-1", Quote("Un petit pas suffit pour commencer la journée.", "Météo & Citation")),
                Entry("neutral-2", Quote("Prenons le temps de remarquer ce qui nous fait du bien.", "Météo & Citation")),
                Entry("neutral-3", Quote("Chaque journée laisse une place à une belle surprise.", "Météo & Citation"))
            )
        }
    }
    
}
