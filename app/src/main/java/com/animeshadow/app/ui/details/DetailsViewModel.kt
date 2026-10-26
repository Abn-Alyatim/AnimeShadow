package com.animeshadow.app.ui.details

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.RatingParts
import com.animeshadow.app.data.translate.SynopsisPrefetcher
import com.animeshadow.app.data.translate.SynopsisTranslator
import com.animeshadow.app.data.translate.TranslationQuotaException
import com.animeshadow.app.data.Refresh
import com.animeshadow.app.data.local.AnimeCharacterEntity
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AnimeStaffEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.RecRow
import com.animeshadow.app.data.local.RelatedRow
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.domain.RecommendationEngine
import com.animeshadow.app.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DetailsUi(
    val anime: AnimeEntity? = null,
    val entry: LibraryEntryEntity? = null,
    val watched: Set<Int> = emptySet(),
    val favorite: Boolean = false,
    val characters: List<AnimeCharacterEntity> = emptyList(),
    val staff: List<AnimeStaffEntity> = emptyList(),
    val relations: List<RelatedRow> = emptyList(),
    val recommendations: List<RecRow> = emptyList(),
    val similar: List<AnimeEntity> = emptyList(),
    val refresh: Refresh? = null,
    /** True while the on-device EN->AR translation of the synopsis runs. */
    val translating: Boolean = false,
) {
    /** Episodes to list: the announced total, else what is known to be out, else the user's progress. */
    val episodeCount: Int get() {
        val a = anime ?: return 0
        return a.episodes ?: maxOf(a.latestEpisode ?: 0, entry?.progress ?: 0, watched.maxOrNull() ?: 0)
    }
    val progress: Int get() = entry?.progress ?: 0
    val progressFraction: Float get() = anime?.episodes?.takeIf { it > 0 }?.let { (progress.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
}

/** Small holder so the 10-argument combine stays readable. */
private data class Mine(val entry: LibraryEntryEntity?, val watched: List<Int>, val favorite: Boolean)
private data class Extra(val chars: List<AnimeCharacterEntity>, val staff: List<AnimeStaffEntity>, val rel: List<RelatedRow>, val recs: List<RecRow>)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: AnimeRepository,
    private val library: LibraryRepository,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
    private val translator: SynopsisTranslator,
) : ViewModel() {
    val animeId: Int = checkNotNull(savedState.get<Int>("id"))
    private val refreshState = MutableStateFlow<Refresh?>(null)
    private val similar = MutableStateFlow<List<AnimeEntity>>(emptyList())

    private val mine = combine(library.observeEntry(animeId), library.observeWatched(animeId), library.observeIsFavoriteAnime(animeId)) { e, w, f -> Mine(e, w, f) }
    private val extra = combine(repo.observeCharacters(animeId), repo.observeStaff(animeId), repo.observeRelations(animeId), repo.observeRecommendations(animeId)) { c, s, r, rc -> Extra(c, s, r, rc) }

    private val translating = MutableStateFlow(false)
    private val status = combine(refreshState, translating) { r, t -> r to t }

    val ui: StateFlow<DetailsUi> = combine(repo.observeAnime(animeId), mine, extra, similar, status) { a, m, x, sim, st ->
        DetailsUi(a, m.entry, m.watched.toSet(), m.favorite, x.chars, x.staff, x.rel, x.recs, sim, st.first, st.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailsUi())

    /** Last "mark all / unmark all" so the snackbar's undo can restore it. */
    private var lastBulk: LibraryRepository.WatchedSnapshot? = null

    init {
        refresh(false)
    }

    fun refresh(force: Boolean) = viewModelScope.launch {
        refreshState.value = repo.refreshDetail(animeId, force)
        translateSynopsis()
        computeSimilar()
    }

    /**
     * Arabic synopsis. Normally the background translator (SynopsisPrefetcher) has it cached before the page opens;
     * if not, it is translated now (online, names kept) and cached. English stays only when that is impossible.
     */
    private suspend fun translateSynopsis() {
        val a = repo.anime(animeId) ?: return
        val english = a.description?.takeIf { it.isNotBlank() } ?: return
        if (a.arabicSynopsis != null) return
        translating.value = true
        try {
            val ar = try {
                translator.translate(english, SynopsisPrefetcher.keepFor(a))
            } catch (_: TranslationQuotaException) {
                null
            }
            if (!ar.isNullOrBlank()) repo.saveArabicSynopsis(animeId, english, ar)
        } finally {
            translating.value = false
        }
    }

    private suspend fun computeSimilar() {
        val target = repo.anime(animeId) ?: return
        val candidates = repo.cachedCandidates()
        val recIds = repo.recommendationsFor(listOf(animeId)).map { it.recId }.toSet()
        similar.value = withContext(Dispatchers.Default) {
            RecommendationEngine.similar(target, candidates).filter { it.id !in recIds }
        }
    }

    fun setStatus(status: WatchStatus) = viewModelScope.launch { library.setStatus(animeId, status) }
    fun remove() = viewModelScope.launch {
        library.remove(animeId)
        SyncScheduler.cancelReminder(context, animeId)
    }
    fun setScore(score: Int?) = viewModelScope.launch { library.setUserScore(animeId, score) }
    fun setRating(parts: RatingParts) = viewModelScope.launch { library.setRating(animeId, parts) }

    /** Item 14: small eye in the episodes bar. All watched -> unmark all; otherwise mark all. Returns true if it marked. */
    fun toggleAllWatched(onDone: (marked: Boolean) -> Unit = {}) = viewModelScope.launch {
        val u = ui.value
        val count = u.episodeCount
        val allWatched = count > 0 && (1..count).all { it in u.watched }
        lastBulk = if (allWatched) library.unmarkAllWatched(animeId) else library.markAllWatched(animeId, count)
        onDone(!allWatched)
    }

    fun undoBulk() = viewModelScope.launch {
        lastBulk?.let { library.restore(it) }
        lastBulk = null
    }
    fun increment() = viewModelScope.launch { library.increment(animeId) }
    fun decrement() = viewModelScope.launch { library.decrement(animeId) }
    fun setEpisode(episode: Int, watched: Boolean) = viewModelScope.launch { library.setEpisodeWatched(animeId, episode, watched) }
    fun markUpTo(episode: Int) = viewModelScope.launch { library.markUpTo(animeId, episode) }
    fun toggleFavorite() = viewModelScope.launch { library.setFavoriteAnime(animeId, !ui.value.favorite) }

    fun setReminder(on: Boolean) = viewModelScope.launch {
        library.setReminder(animeId, on)
        val a = repo.anime(animeId) ?: return@launch
        runCatching { if (on) SyncScheduler.scheduleReminder(context, a, clock.now()) else SyncScheduler.cancelReminder(context, animeId) }
    }
}
