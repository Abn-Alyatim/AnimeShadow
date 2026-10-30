package com.animeshadow.app.data

import androidx.room.withTransaction
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.FavoriteAnimeEntity
import com.animeshadow.app.data.local.FavoriteCharacterEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.local.WatchedEpisodeEntity

/**
 * The user's own data. Everything here is local (Room) and works offline.
 *
 * Progress rule: progress = highest watched episode. "+" marks the next episode watched, "-" un-marks the current
 * one; ticking an episode in the list marks it and moves progress to the highest ticked episode. Reaching the last
 * episode of a finished show moves it to Completed. Watching an episode of a title not in the library adds it as
 * Watching.
 */
/** The four rating sliders (0–10 each). */
data class RatingParts(val story: Int = 0, val characters: Int = 0, val animation: Int = 0, val music: Int = 0) {
    val overall: Double get() = (story + characters + animation + music) / 4.0
    val isEmpty: Boolean get() = story == 0 && characters == 0 && animation == 0 && music == 0

    companion object {
        fun of(entry: LibraryEntryEntity?): RatingParts {
            if (entry == null) return RatingParts()
            val s = entry.scoreStory
            return if (s != null) RatingParts(s, entry.scoreCharacters ?: 0, entry.scoreAnimation ?: 0, entry.scoreMusic ?: 0)
            else entry.userScore?.let { RatingParts(it, it, it, it) } ?: RatingParts()
        }
    }
}

class LibraryRepository(private val db: AppDatabase, private val clock: Clock) {
    private val dao = db.libraryDao()
    private val animeDao = db.animeDao()

    fun observeLibrary() = dao.observeAll()
    fun observeEntry(animeId: Int) = dao.observeEntry(animeId)
    fun observeWatched(animeId: Int) = dao.observeWatched(animeId)
    fun observeHistory() = dao.observeHistory()
    fun observeFavoriteAnime() = dao.observeFavoriteAnime()
    fun observeIsFavoriteAnime(animeId: Int) = dao.observeIsFavoriteAnime(animeId)
    fun observeFavoriteCharacters() = dao.observeFavoriteCharacters()
    fun observeIsFavoriteCharacter(id: Int) = dao.observeIsFavoriteCharacter(id)
    fun observeFavoriteAnimeCount() = dao.observeFavoriteAnimeCount()
    suspend fun libraryItems() = dao.all()
    suspend fun entry(animeId: Int) = dao.get(animeId)
    suspend fun watchingIds() = dao.watchingIds()

    suspend fun setStatus(animeId: Int, status0: WatchStatus) = db.withTransaction {
        val now = clock.now()
        val old = dao.get(animeId)
        // v1.5.0: no «شاهدته» before the show has finished airing
        val status = if (status0 == WatchStatus.COMPLETED && !com.animeshadow.app.domain.Airing.canComplete(animeDao.get(animeId))) WatchStatus.WATCHING else status0
        var progress = old?.progress ?: 0
        if (status == WatchStatus.COMPLETED) {
            val total = animeDao.get(animeId)?.episodes
            if (total != null && total > 0 && progress < total) {
                markRange(animeId, progress + 1, total, now)
                progress = total
            }
        }
        dao.upsert(
            old?.copy(status = status, progress = progress, updatedAt = now)
                ?: LibraryEntryEntity(animeId, status, progress, null, now, now),
        )
    }

    suspend fun remove(animeId: Int) = db.withTransaction {
        dao.delete(animeId)
        dao.deleteAllWatched(animeId)
    }

    suspend fun setUserScore(animeId: Int, score: Int?) = db.withTransaction {
        val now = clock.now()
        val s = score?.coerceIn(1, 10)
        val old = dao.get(animeId)
        dao.upsert(old?.copy(userScore = s, updatedAt = now) ?: LibraryEntryEntity(animeId, WatchStatus.PLAN_TO_WATCH, 0, s, now, now))
    }

    /**
     * Rating dialog (ref 03): four sliders 0–10. userScore = their average rounded (0 everywhere clears the rating).
     */
    suspend fun setRating(animeId: Int, rating: RatingParts) = db.withTransaction {
        val now = clock.now()
        val old = dao.get(animeId)
        val cleared = rating.isEmpty
        val whole = if (cleared) null else Math.round(rating.overall).toInt().coerceIn(1, 10)
        val base = old ?: LibraryEntryEntity(animeId, WatchStatus.PLAN_TO_WATCH, 0, null, now, now)
        dao.upsert(
            base.copy(
                userScore = whole,
                scoreStory = rating.story.takeUnless { cleared }, scoreCharacters = rating.characters.takeUnless { cleared },
                scoreAnimation = rating.animation.takeUnless { cleared }, scoreMusic = rating.music.takeUnless { cleared },
                updatedAt = now,
            ),
        )
    }

    /** What the episodes "eye" button changed, so the snackbar's undo can put it back exactly. */
    data class WatchedSnapshot(val animeId: Int, val entry: LibraryEntryEntity?, val watched: List<WatchedEpisodeEntity>)

    private suspend fun snapshot(animeId: Int) = WatchedSnapshot(animeId, dao.get(animeId), dao.allWatched().filter { it.animeId == animeId })

    /** Small eye in the episodes top bar: marks episodes 1..[count] watched. Returns the state before, for undo. */
    suspend fun markAllWatched(animeId: Int, count: Int): WatchedSnapshot = db.withTransaction {
        val before = snapshot(animeId)
        if (count > 0) {
            val now = clock.now()
            markRange(animeId, 1, count, now)
            writeProgress(animeId, dao.maxWatched(animeId) ?: 0, now)
        }
        before
    }

    /** Second tap on the eye: un-marks every episode. Returns the state before, for undo. */
    suspend fun unmarkAllWatched(animeId: Int): WatchedSnapshot = db.withTransaction {
        val before = snapshot(animeId)
        dao.deleteAllWatched(animeId)
        if (before.entry != null) writeProgress(animeId, 0, clock.now())
        before
    }

    /** Undo for the two calls above: restores the watched episodes and the library entry exactly as they were. */
    suspend fun restore(snapshot: WatchedSnapshot) = db.withTransaction {
        dao.deleteAllWatched(snapshot.animeId)
        if (snapshot.watched.isNotEmpty()) dao.insertWatched(snapshot.watched)
        if (snapshot.entry != null) dao.upsert(snapshot.entry) else dao.delete(snapshot.animeId)
    }

    suspend fun setReminder(animeId: Int, on: Boolean) = db.withTransaction {
        val now = clock.now()
        val old = dao.get(animeId)
        dao.upsert(old?.copy(remind = on, updatedAt = now) ?: LibraryEntryEntity(animeId, WatchStatus.WATCHING, 0, null, now, now, remind = on))
    }

    private suspend fun markRange(animeId: Int, from: Int, to: Int, now: Long) {
        if (to < from) return
        dao.insertWatched((from..to).map { WatchedEpisodeEntity(animeId, it, now) })
    }

    private fun maxEpisodes(anime: AnimeEntity?): Int? = anime?.episodes?.takeIf { it > 0 }

    private suspend fun writeProgress(animeId: Int, progress: Int, now: Long) {
        val anime = animeDao.get(animeId)
        val total = maxEpisodes(anime)
        val old = dao.get(animeId)
        var status = old?.status ?: WatchStatus.WATCHING
        if (status == WatchStatus.PLAN_TO_WATCH && progress > 0) status = WatchStatus.WATCHING
        if (total != null && progress >= total && anime?.status == "FINISHED") status = WatchStatus.COMPLETED
        if (status == WatchStatus.COMPLETED && total != null && progress < total) status = WatchStatus.WATCHING
        dao.upsert(
            old?.copy(progress = progress, status = status, updatedAt = now)
                ?: LibraryEntryEntity(animeId, status, progress, null, now, now),
        )
    }

    /** "+": marks episode progress+1. Returns the new progress. */
    suspend fun increment(animeId: Int): Int = db.withTransaction {
        val now = clock.now()
        val current = dao.get(animeId)?.progress ?: 0
        val a = animeDao.get(animeId)
        // v1.5.0: an airing show only counts up to the episodes already out
        val total = if (a != null && a.status != null && a.status != "FINISHED") com.animeshadow.app.domain.Airing.released(a, current).takeIf { it > 0 } ?: 0 else maxEpisodes(a)
        val next = current + 1
        if (total != null && next > total) return@withTransaction current
        dao.insertWatched(listOf(WatchedEpisodeEntity(animeId, next, now)))
        writeProgress(animeId, next, now)
        next
    }

    /** "-": un-marks the current episode. Returns the new progress. */
    suspend fun decrement(animeId: Int): Int = db.withTransaction {
        val now = clock.now()
        val current = dao.get(animeId)?.progress ?: return@withTransaction 0
        if (current <= 0) return@withTransaction 0
        dao.deleteWatchedAbove(animeId, current - 1)
        val newProgress = dao.maxWatched(animeId) ?: 0
        writeProgress(animeId, newProgress.coerceAtMost(current - 1), now)
        newProgress.coerceAtMost(current - 1)
    }

    /** Episode checkbox. Returns the new progress. */
    suspend fun setEpisodeWatched(animeId: Int, episode: Int, watched: Boolean): Int = db.withTransaction {
        require(episode >= 1) { "episode must be >= 1" }
        val now = clock.now()
        if (watched) dao.insertWatched(listOf(WatchedEpisodeEntity(animeId, episode, now))) else dao.deleteWatched(animeId, episode)
        val progress = dao.maxWatched(animeId) ?: 0
        writeProgress(animeId, progress, now)
        progress
    }

    /** Marks every episode up to [episode] (long-press "watched up to here"). */
    suspend fun markUpTo(animeId: Int, episode: Int): Int = db.withTransaction {
        val now = clock.now()
        markRange(animeId, 1, episode, now)
        val progress = dao.maxWatched(animeId) ?: 0
        writeProgress(animeId, progress, now)
        progress
    }

    suspend fun setFavoriteAnime(animeId: Int, favorite: Boolean) {
        if (favorite) dao.insertFavoriteAnime(FavoriteAnimeEntity(animeId, clock.now())) else dao.deleteFavoriteAnime(animeId)
    }

    suspend fun setFavoriteCharacter(
        id: Int, name: String, image: String?, animeId: Int?, animeTitle: String?, favorite: Boolean,
    ) {
        if (favorite) dao.insertFavoriteCharacter(FavoriteCharacterEntity(id, name, image, animeId, animeTitle, clock.now()))
        else dao.deleteFavoriteCharacter(id)
    }

    suspend fun markSeenEpisode(animeId: Int, episode: Int) {
        dao.get(animeId)?.let { dao.upsert(it.copy(lastSeenEpisode = episode)) }
    }

    suspend fun clearUserData() = db.withTransaction {
        dao.clearLibrary(); dao.clearWatched(); dao.clearFavoriteAnime(); dao.clearFavoriteCharacters()
    }
    suspend fun clearHistory() = dao.clearWatched()
}
