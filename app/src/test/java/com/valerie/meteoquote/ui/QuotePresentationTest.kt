package com.valerie.meteoquote.ui

import org.junit.Assert.*
import org.junit.Test

class QuotePresentationTest {
    @Test fun separatesAuthorButKeepsDashesInsideText() {
        assertEquals(QuotePresentation("Le ciel — et nous.", "Auteur"),
            QuotePresentation.from("\"Le ciel — et nous.\" — Auteur"))
    }
    @Test fun preservesUnattributedTextAndMultilineQuotes() {
        assertEquals(QuotePresentation("Texte sans auteur", null), QuotePresentation.from("Texte sans auteur"))
        assertEquals(QuotePresentation("Une ligne\nPuis une autre", "Auteur"),
            QuotePresentation.from("\"Une ligne\nPuis une autre\" — Auteur"))
        assertNull(QuotePresentation.from("—").author)
    }
}
