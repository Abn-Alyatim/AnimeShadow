package com.animeshadow.app.data.translate

import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.local.AnimeEntity

/**
 * Background pre-translation: takes the synopses that have no Arabic yet (titles in the user's list first, then what
 * was loaded most recently: home, search, studio and genre lists) and translates them one by one, so a details page
 * normally opens already in Arabic. Stops for the day when the free service's quota is used up.
 */
class SynopsisPrefetcher(private val repo: AnimeRepository, private val translator: SynopsisTranslator) {
    data class Outcome(val translated: Int, val failed: Int, val quotaReached: Boolean, val morePending: Boolean)

    suspend fun run(max: Int = 40): Outcome {
        var done = 0
        var failed = 0
        val seen = HashSet<Int>()
        while (done + failed < max) {
            val batch = repo.pendingTranslations(minOf(10, max - done - failed)).filter { seen.add(it.id) }
            if (batch.isEmpty()) return Outcome(done, failed, quotaReached = false, morePending = false)
            for (a in batch) {
                val english = a.description ?: continue
                val ar = try {
                    translator.translate(english, keepFor(a))
                } catch (_: TranslationQuotaException) {
                    return Outcome(done, failed, quotaReached = true, morePending = true)
                }
                if (ar.isNullOrBlank()) {
                    repo.markTranslationFailed(a.id)
                    failed++
                } else {
                    repo.saveArabicSynopsis(a.id, english, ar)
                    done++
                }
            }
        }
        return Outcome(done, failed, quotaReached = false, morePending = repo.pendingTranslations(1).isNotEmpty())
    }

    companion object {
        /** Names the translator keeps as written: the show's titles and its main characters. */
        fun keepFor(a: AnimeEntity): List<String> =
            listOfNotNull(a.titleEnglish, a.titleRomaji).distinct().map { MyMemoryTranslator.TITLE_PREFIX + it } +
                a.charNames.orEmpty().split('|').filter { it.isNotBlank() }
    }
}
