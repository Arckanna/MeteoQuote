package com.valerie.meteoquote.data

interface QuoteRepository {
    /** Reads today's existing assignment without choosing an atmosphere prematurely. */
    fun assignedQuote(): String?
    /** Returns the phone-local day's assignment; null means no usable weather is available. */
    fun getQuote(code: Int?): String
}
