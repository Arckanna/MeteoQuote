package com.valerie.meteoquote.data

import java.time.LocalDate

interface QuoteRepository {
    fun getQuote(date: LocalDate, code: Int, advance: Boolean = false): String
}
