package com.valerie.meteoquote.ui

/** Shared presentation palette for Views and Compose. */
object WeatherMood {
    fun forCode(code: Int): Pair<Int, String> = when (code) {
            0 -> 0xFFFFF0D7.toInt() to "Quelques mots sous un ciel clair"
            1, 2, 3 -> 0xFFE7EFF2.toInt() to "Quelques mots au fil des nuages"
            45, 48 -> 0xFFEBEEEB.toInt() to "Quelques mots dans la brume"
            71, 73, 75, 77, 85, 86 -> 0xFFE7F2F4.toInt() to "Quelques mots dans le calme de la neige"
            95, 96, 99 -> 0xFFEEEBF4.toInt() to "Quelques mots à travers l’orage"
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> 0xFFE6EEF7.toInt() to "Quelques mots au rythme de la pluie"
            else -> 0xFFEBEEEB.toInt() to "À l’humeur du ciel"
        }
}
