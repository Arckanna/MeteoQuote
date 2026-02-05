package com.valerie.meteoquote.data

import android.content.Context
import com.valerie.meteoquote.data.model.Quote
import java.time.LocalDate

class QuoteRepository(private val context: Context) {
    
    private fun prefs() = context.getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)
    
    private fun bucketName(code: Int): String = when (code) {
        0 -> "clear"
        1, 2, 3 -> "clouds"
        45, 48 -> "fog"
        51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> "rain"
        71, 73, 75, 85, 86 -> "snow"
        95, 96, 99 -> "thunder"
        else -> "clouds"
    }
    
    private fun quotesFor(code: Int): List<Quote> {
        val clear = listOf(
            Quote("Le soleil brille pour tout le monde.", "Proverbe"),
            Quote("La simplicité est la sophistication suprême.", "L. de Vinci"),
            Quote("Crée la lumière que tu cherches.", "Anonyme")
        )
        val clouds = listOf(
            Quote("Au-dessus des nuages, le ciel est toujours bleu.", "Proverbe"),
            Quote("Patience : les nuages se dissipent toujours.", "Anonyme"),
            Quote("Notre clarté naît parfois de l'ombre.", "Anonyme")
        )
        val rain = listOf(
            Quote("Sans la pluie, rien ne pousse.", "Anonyme"),
            Quote("Il faut de la pluie pour voir l'arc-en-ciel.", "D. Parton"),
            Quote("Chaque goutte prépare une moisson.", "Proverbe")
        )
        val snow = listOf(
            Quote("La paix tombe parfois comme la neige.", "Anonyme"),
            Quote("Chaque flocon a sa forme et son destin.", "Proverbe"),
            Quote("Le silence de la neige dit l'essentiel.", "Anonyme")
        )
        val fog = listOf(
            Quote("Quand la route est brumeuse, avance pas à pas.", "Anonyme"),
            Quote("La clarté se révèle en chemin.", "Anonyme"),
            Quote("Tout brouillard finit par se lever.", "Proverbe")
        )
        val thunder = listOf(
            Quote("Le courage n'est pas l'absence de peur.", "N. Mandela"),
            Quote("L'éclair éclaire l'instant : saisis-le.", "Anonyme"),
            Quote("La tempête forge les marins.", "Proverbe")
        )
        return when (code) {
            0 -> clear
            1, 2, 3 -> clouds
            45, 48 -> fog
            51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82, 66, 67 -> rain
            71, 73, 75, 85, 86 -> snow
            95, 96, 99 -> thunder
            else -> clouds
        }
    }
    
    /**
     * Renvoie une citation formatée. Si advance=true, passe à la suivante et persiste l'index.
     */
    fun getQuote(date: LocalDate, code: Int, advance: Boolean = false): String {
        val bucket = quotesFor(code)
        val key = "quote_idx_${bucketName(code)}"
        val p = prefs()
        var idx = p.getInt(key, (date.dayOfYear - 1) % bucket.size)
        if (advance) idx = (idx + 1) % bucket.size
        p.edit().putInt(key, idx).apply()
        val q = bucket[idx]
        return "\"${q.text}\" — ${q.author}"
    }
}
