package com.valerie.meteoquote.ui

/** Separates the repository's existing display format without changing quote selection. */
data class QuotePresentation(val text: String, val author: String?) {
    companion object {
        fun from(formatted: String): QuotePresentation {
            val separator = formatted.lastIndexOf("\" — ")
            return if (formatted.startsWith("\"") && separator > 0) {
                QuotePresentation(formatted.substring(1, separator), formatted.substring(separator + 4).trim().ifEmpty { null })
            } else QuotePresentation(formatted, null)
        }
    }
}
