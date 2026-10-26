package com.animeshadow.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Cached anime metadata from AniList (id = AniList media id; idMal kept for MyAnimeList/Jikan enrichment). */
@Entity(
    tableName = "anime",
    indices = [Index("status"), Index("seasonYear", "season"), Index("nextAiringAt")],
)
data class AnimeEntity(
    @PrimaryKey val id: Int,
    val idMal: Int? = null,
    val titleRomaji: String? = null,
    val titleEnglish: String? = null,
    val titleNative: String? = null,
    val coverLarge: String? = null,
    val coverXL: String? = null,
    val color: String? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val score: Int? = null,
    val popularity: Int? = null,
    val genres: String = "",
    val studio: String? = null,
    val nextEpisode: Int? = null,
    val nextAiringAt: Long? = null,
    val cardUpdatedAt: Long = 0,
    // Detail-only fields (filled by the details request, preserved when a list refresh updates the card fields).
    val banner: String? = null,
    val description: String? = null,
    val source: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val isAdult: Boolean = false,
    val trailerId: String? = null,
    val trailerSite: String? = null,
    val trailerThumb: String? = null,
    val scoreDist: String? = null,
    val statusDist: String? = null,
    val episodeTitles: String? = null,
    val malScore: Double? = null,
    val malMembers: Int? = null,
    val detailFetchedAt: Long? = null,
    // v1.1 (DB version 2): MyAnimeList enrichment (Jikan, else the MAL page) and the on-device Arabic synopsis.
    /** MAL main picture (large). Null until Jikan or the MAL page answered; the UI then uses AniList's cover. */
    val malPoster: String? = null,
    /** MAL content rating as MAL writes it, e.g. "PG-13 - Teens 13 or older". */
    val malRating: String? = null,
    val malScoredBy: Int? = null,
    /** Which source filled the MAL fields: "jikan" or "mal_page". */
    val malSource: String? = null,
    val malFetchedAt: Long? = null,
    /** Machine translation (EN -> AR) of [description]; valid only while [descriptionArSrc] == description.hashCode(). */
    val descriptionAr: String? = null,
    val descriptionArSrc: Int? = null,
    // v1.1.1 (DB version 3): names kept untranslated in the synopsis, and the last failed translation attempt.
    /** Main character names ("|"-joined, from AniList) that the translator keeps as written. */
    val charNames: String? = null,
    /** When the background translator last failed on this row (null = never tried or succeeded). */
    val arFailedAt: Long? = null,
) {
    val displayTitle: String get() = (titleEnglish ?: titleRomaji)?.let(::prettyTitle) ?: titleNative ?: "#$id"

    /** English title as the references show it (MyAnimeList style), for the details header and info grid. */
    val englishTitle: String? get() = (titleEnglish ?: titleRomaji)?.let(::prettyTitle)

    /** Poster for details/lists: the MAL main picture when known (ref 11), else AniList's extra-large cover. */
    val posterUrl: String? get() = malPoster ?: coverXL ?: coverLarge
    val gridPoster: String? get() = malPoster ?: coverLarge

    /** The cached Arabic synopsis, if it was translated from the current English text. */
    val arabicSynopsis: String? get() = descriptionAr?.takeIf { description != null && descriptionArSrc == description.hashCode() }
    val genreList: List<String> get() = if (genres.isBlank()) emptyList() else genres.split('|')

    /** Highest episode known to be out: total for finished shows, next-1 for airing ones. */
    val latestEpisode: Int? get() = when {
        nextEpisode != null -> (nextEpisode - 1).takeIf { it > 0 }
        status == "FINISHED" -> episodes
        else -> null
    }

    /** Keep detail-only fields of an already cached row when a list response refreshes the card fields. */
    fun mergedWith(old: AnimeEntity?): AnimeEntity = if (old == null) this else copy(
        banner = old.banner, source = old.source, startDate = old.startDate,
        endDate = old.endDate, isAdult = old.isAdult, trailerId = old.trailerId, trailerSite = old.trailerSite,
        trailerThumb = old.trailerThumb, scoreDist = old.scoreDist, statusDist = old.statusDist,
        episodeTitles = old.episodeTitles, malScore = old.malScore, malMembers = old.malMembers,
        detailFetchedAt = old.detailFetchedAt, studio = studio ?: old.studio,
        malPoster = old.malPoster, malRating = old.malRating, malScoredBy = old.malScoredBy, malSource = old.malSource,
        malFetchedAt = old.malFetchedAt, descriptionArSrc = old.descriptionArSrc,
        descriptionAr = old.descriptionAr?.takeIf { description == null || description == old.description },
        // Lists now carry the synopsis and main names too (for background translation); keep the cached ones otherwise.
        description = description ?: old.description, charNames = charNames ?: old.charNames, arFailedAt = old.arFailedAt,
    )
}

/** Ordered membership of an anime in a remote list (home airing, a season page, top rated, a search...). */
@Entity(tableName = "feed_item", primaryKeys = ["feed", "animeId"], indices = [Index("feed", "position")])
data class FeedItemEntity(val feed: String, val animeId: Int, val position: Int)

@Entity(tableName = "feed_meta")
data class FeedMetaEntity(@PrimaryKey val feed: String, val fetchedAt: Long, val pages: Int, val hasNext: Boolean)

@Entity(tableName = "airing", primaryKeys = ["animeId", "episode"], indices = [Index("airingAt")])
data class AiringEntity(val animeId: Int, val episode: Int, val airingAt: Long)

enum class WatchStatus { WATCHING, PLAN_TO_WATCH, COMPLETED, ON_HOLD, DROPPED }

@Entity(tableName = "library", indices = [Index("status"), Index("addedAt")])
data class LibraryEntryEntity(
    @PrimaryKey val animeId: Int,
    val status: WatchStatus,
    val progress: Int = 0,
    val userScore: Int? = null,
    val addedAt: Long,
    val updatedAt: Long,
    val lastSeenEpisode: Int? = null,
    val remind: Boolean = false,
    // v1.1: the four rating sliders (0–10). userScore = their average, rounded.
    val scoreStory: Int? = null,
    val scoreCharacters: Int? = null,
    val scoreAnimation: Int? = null,
    val scoreMusic: Int? = null,
) {
    /** The exact overall shown as "X/10": average of the sliders when set, else the stored whole score. */
    val overallScore: Double? get() {
        val parts = listOfNotNull(scoreStory, scoreCharacters, scoreAnimation, scoreMusic)
        return if (parts.size == 4) parts.average() else userScore?.toDouble()
    }
}

/** History of new-episode / reminder alerts shown in the Notifications screen (drawer bell). */
@Entity(tableName = "notification_log", indices = [Index("createdAt"), Index("read")])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val animeId: Int,
    val episode: Int,
    val title: String,
    val image: String?,
    /** "new_episode" or "reminder". */
    val kind: String,
    val createdAt: Long,
    val read: Boolean = false,
)

@Entity(tableName = "watched_episode", primaryKeys = ["animeId", "episode"], indices = [Index("watchedAt")])
data class WatchedEpisodeEntity(val animeId: Int, val episode: Int, val watchedAt: Long)

@Entity(tableName = "favorite_anime", indices = [Index("addedAt")])
data class FavoriteAnimeEntity(@PrimaryKey val animeId: Int, val addedAt: Long)

@Entity(tableName = "favorite_character", indices = [Index("addedAt")])
data class FavoriteCharacterEntity(
    @PrimaryKey val characterId: Int,
    val name: String,
    val image: String?,
    val animeId: Int?,
    val animeTitle: String?,
    val addedAt: Long,
)

@Entity(tableName = "anime_character", primaryKeys = ["animeId", "characterId"], indices = [Index("characterId")])
data class AnimeCharacterEntity(
    val animeId: Int,
    val characterId: Int,
    val name: String,
    val nameNative: String?,
    val image: String?,
    val role: String?,
    val vaId: Int?,
    val vaName: String?,
    val vaImage: String?,
    val position: Int,
)

@Entity(tableName = "anime_staff", primaryKeys = ["animeId", "staffId", "role"])
data class AnimeStaffEntity(
    val animeId: Int,
    val staffId: Int,
    val name: String,
    val image: String?,
    val role: String,
    val position: Int,
)

@Entity(tableName = "anime_relation", primaryKeys = ["animeId", "relatedId"])
data class RelationEntity(val animeId: Int, val relatedId: Int, val relationType: String, val position: Int)

@Entity(tableName = "anime_recommendation", primaryKeys = ["animeId", "recId"], indices = [Index("recId")])
data class RecommendationEntity(val animeId: Int, val recId: Int, val rating: Int, val position: Int)

/** Character page cache (bio + appearances JSON). */
@Entity(tableName = "character")
data class CharacterEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val nameNative: String?,
    val image: String?,
    val description: String?,
    val favourites: Int?,
    val appearancesJson: String,
    val fetchedAt: Long,
)

/** Voice actor / staff page cache (bio + roles JSON). */
@Entity(tableName = "staff")
data class StaffEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val nameNative: String?,
    val image: String?,
    val description: String?,
    val rolesJson: String,
    val fetchedAt: Long,
)

/**
 * AniList writes a few English titles in capitals ("ONE PIECE"); MyAnimeList, which the reference screenshots show,
 * writes "One Piece". All-caps titles of two or more words (one of at least 4 letters) are shown in title case;
 * single words and short acronyms ("SPY×FAMILY", "DAN DA DAN") are left as they are.
 */
fun prettyTitle(t: String): String {
    val words = t.split(' ').filter { it.isNotEmpty() }
    val letters = t.filter { it.isLetter() }
    if (words.size < 2 || letters.isEmpty() || letters.any { it.isLowerCase() } || words.none { w -> w.count { it.isLetter() } >= 4 }) return t
    return words.joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase() } }
}
