package com.valerie.meteoquote.data

import android.content.Context
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** One assignment per phone-local date, shared by every repository instance in this process. */
class LocalQuoteRepository(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val phoneZone: () -> ZoneId = { ZoneId.systemDefault() }
) : QuoteRepository {
    private val prefs = context.applicationContext.getSharedPreferences("meteoquote_prefs", Context.MODE_PRIVATE)

    override fun assignedQuote(): String? = synchronized(assignmentLock) {
        QuoteCatalog.find(string("daily_quote_v1_${LocalDate.ofInstant(clock.instant(), phoneZone())}"))?.formatted
    }

    override fun getQuote(code: Int?): String = synchronized(assignmentLock) {
        val date = LocalDate.ofInstant(clock.instant(), phoneZone())
        val key = "daily_quote_v1_$date"
        QuoteCatalog.find(string(key))?.let { return@synchronized it.formatted }

        val entries = QuoteCatalog.entries(code)
        val defaultIndex = Math.floorMod(date.toEpochDay(), entries.size.toLong()).toInt()
        val migrated = try { prefs.getBoolean("daily_quote_migrated_v1", false) }
            catch (_: ClassCastException) { false }
        // Preserve the old category's current index for the first attribution after upgrade.
        val initialIndex = if (!migrated) {
            try { Math.floorMod(prefs.getInt("quote_idx_${QuoteCatalog.category(code)}", defaultIndex), entries.size) }
            catch (_: ClassCastException) { defaultIndex }
        } else defaultIndex
        val previousId = string("daily_quote_v1_${date.minusDays(1)}") ?: string("daily_quote_last_id_v1")
        val selected = if (entries[initialIndex].id == previousId && entries.size > 1)
            entries[(initialIndex + 1) % entries.size] else entries[initialIndex]

        // apply updates SharedPreferences atomically in memory before releasing the shared lock.
        prefs.edit().putString(key, selected.id)
            .putString("daily_quote_last_id_v1", selected.id)
            .putBoolean("daily_quote_migrated_v1", true).apply()
        selected.formatted
    }

    private fun string(key: String): String? = try { prefs.getString(key, null) }
        catch (_: ClassCastException) { null }

    private companion object {
        val assignmentLock = Any()
    }
}
