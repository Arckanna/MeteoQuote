package com.valerie.meteoquote.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class QuoteRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var repository: QuoteRepository

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putInt(any(), any()) } returns mockEditor
        every { mockEditor.apply() } returns Unit

        repository = QuoteRepository(mockContext)
    }

    @Test
    fun `getQuote should return a quote for clear weather`() {
        every { mockPrefs.getInt(any(), any()) } returns 0

        val quote = repository.getQuote(LocalDate.now(), 0, advance = false)

        assertTrue(quote.contains("\""))
        assertTrue(quote.contains("—"))
        verify { mockPrefs.getInt("quote_idx_clear", any()) }
    }

    @Test
    fun `getQuote should return a quote for rain weather`() {
        every { mockPrefs.getInt(any(), any()) } returns 0

        val quote = repository.getQuote(LocalDate.now(), 61, advance = false)

        assertTrue(quote.contains("\""))
        assertTrue(quote.contains("—"))
        verify { mockPrefs.getInt("quote_idx_rain", any()) }
    }

    @Test
    fun `getQuote should advance to next quote when advance is true`() {
        every { mockPrefs.getInt(any(), any()) } returns 0

        repository.getQuote(LocalDate.now(), 0, advance = true)

        // Vérifier que l'index est incrémenté et sauvegardé
        verify { mockEditor.putInt("quote_idx_clear", 1) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `getQuote should persist index when advance is true`() {
        every { mockPrefs.getInt(any(), any()) } returns 0

        repository.getQuote(LocalDate.now(), 0, advance = true)

        verify { mockEditor.putInt("quote_idx_clear", 1) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `getQuote should use day of year as default index`() {
        val date = LocalDate.of(2024, 3, 15) // Jour 75 de l'année
        val expectedIndex = (75 - 1) % 3 // 3 citations pour "clear"
        every { mockPrefs.getInt(any(), any()) } returns expectedIndex

        repository.getQuote(date, 0, advance = false)

        verify { mockPrefs.getInt("quote_idx_clear", expectedIndex) }
    }
}
