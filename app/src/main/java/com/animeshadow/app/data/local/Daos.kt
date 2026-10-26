package com.animeshadow.app.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ScheduleRow(@Embedded val anime: AnimeEntity, val airEpisode: Int, val airAt: Long)

data class RelatedRow(@Embedded val anime: AnimeEntity, val relationType: String)

data class RecRow(@Embedded val anime: AnimeEntity, val recRating: Int)

data class HistoryRow(@Embedded val anime: AnimeEntity, val lastEpisode: Int, val lastWatchedAt: Long)

data class FavoriteAnimeRow(@Embedded val anime: AnimeEntity, val favAddedAt: Long)

data class LibraryItem(
    @Embedded val entry: LibraryEntryEntity,
    @Relation(parentColumn = "animeId", entityColumn = "id") val anime: AnimeEntity?,
)

@Dao
interface AnimeDao {
    @Upsert suspend fun upsertAll(items: List<AnimeEntity>)
    @Upsert suspend fun upsert(item: AnimeEntity)

    /**
     * Rows whose English synopsis has no Arabic yet, for the background translator: titles in the user's list first,
     * then the most recently loaded (home, search, studio, genre lists), then the most popular. Rows that failed
     * recently are skipped until [retryBefore].
     */
    @Query(
        "SELECT a.* FROM anime a LEFT JOIN library l ON l.animeId = a.id " +
            "WHERE a.description IS NOT NULL AND a.description != '' AND a.descriptionAr IS NULL " +
            "AND (a.arFailedAt IS NULL OR a.arFailedAt < :retryBefore) " +
            "ORDER BY CASE WHEN l.animeId IS NULL THEN 1 ELSE 0 END, a.cardUpdatedAt DESC, a.popularity DESC LIMIT :limit",
    )
    suspend fun pendingTranslations(retryBefore: Long, limit: Int): List<AnimeEntity>

    @Query("UPDATE anime SET arFailedAt = :at WHERE id = :id")
    suspend fun markTranslationFailed(id: Int, at: Long)

    @Query("SELECT * FROM anime WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Int>): List<AnimeEntity>

    @Query("SELECT * FROM anime WHERE id = :id")
    suspend fun get(id: Int): AnimeEntity?

    @Query("SELECT * FROM anime WHERE id = :id")
    fun observe(id: Int): Flow<AnimeEntity?>

    @Query(
        "SELECT anime.* FROM feed_item JOIN anime ON anime.id = feed_item.animeId " +
            "WHERE feed_item.feed = :feed ORDER BY feed_item.position"
    )
    fun observeFeed(feed: String): Flow<List<AnimeEntity>>

    @Query(
        "SELECT character.* FROM feed_item JOIN character ON character.id = feed_item.animeId " +
            "WHERE feed_item.feed = :feed ORDER BY feed_item.position"
    )
    fun observeCharacterFeed(feed: String): Flow<List<CharacterEntity>>

    @Query("SELECT COUNT(*) FROM feed_item WHERE feed = :feed")
    suspend fun feedSize(feed: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedItems(items: List<FeedItemEntity>)

    @Query("DELETE FROM feed_item WHERE feed = :feed")
    suspend fun clearFeed(feed: String)

    @Query("SELECT * FROM feed_meta WHERE feed = :feed")
    suspend fun feedMeta(feed: String): FeedMetaEntity?

    @Query("SELECT * FROM feed_meta WHERE feed = :feed")
    fun observeFeedMeta(feed: String): Flow<FeedMetaEntity?>

    @Upsert suspend fun upsertFeedMeta(meta: FeedMetaEntity)

    @Query("DELETE FROM feed_meta")
    suspend fun clearAllFeedMeta()

    @Upsert suspend fun upsertAiring(items: List<AiringEntity>)

    @Query("DELETE FROM airing WHERE airingAt < :before")
    suspend fun deleteAiringBefore(before: Long)

    @Query(
        "SELECT anime.*, airing.episode AS airEpisode, airing.airingAt AS airAt FROM airing " +
            "JOIN anime ON anime.id = airing.animeId WHERE airing.airingAt >= :from AND airing.airingAt < :to " +
            "ORDER BY airing.airingAt"
    )
    fun observeSchedule(from: Long, to: Long): Flow<List<ScheduleRow>>

    @Query("SELECT * FROM anime_character WHERE animeId = :animeId ORDER BY position")
    fun observeCharacters(animeId: Int): Flow<List<AnimeCharacterEntity>>

    @Query("SELECT * FROM anime_staff WHERE animeId = :animeId ORDER BY position")
    fun observeStaff(animeId: Int): Flow<List<AnimeStaffEntity>>

    @Query(
        "SELECT anime.*, r.relationType AS relationType FROM anime_relation r JOIN anime ON anime.id = r.relatedId " +
            "WHERE r.animeId = :animeId ORDER BY r.position"
    )
    fun observeRelations(animeId: Int): Flow<List<RelatedRow>>

    @Query(
        "SELECT anime.*, r.rating AS recRating FROM anime_recommendation r JOIN anime ON anime.id = r.recId " +
            "WHERE r.animeId = :animeId ORDER BY r.position"
    )
    fun observeRecommendations(animeId: Int): Flow<List<RecRow>>

    @Query("SELECT * FROM anime_recommendation WHERE animeId IN (:animeIds)")
    suspend fun recommendationsFor(animeIds: List<Int>): List<RecommendationEntity>

    @Query("DELETE FROM anime_character WHERE animeId = :animeId")
    suspend fun clearCharacters(animeId: Int)

    @Query("DELETE FROM anime_staff WHERE animeId = :animeId")
    suspend fun clearStaff(animeId: Int)

    @Query("DELETE FROM anime_relation WHERE animeId = :animeId")
    suspend fun clearRelations(animeId: Int)

    @Query("DELETE FROM anime_recommendation WHERE animeId = :animeId")
    suspend fun clearRecommendations(animeId: Int)

    @Upsert suspend fun upsertCharacters(items: List<AnimeCharacterEntity>)
    @Upsert suspend fun upsertStaff(items: List<AnimeStaffEntity>)
    @Upsert suspend fun upsertRelations(items: List<RelationEntity>)
    @Upsert suspend fun upsertRecommendations(items: List<RecommendationEntity>)

    @Query(
        "SELECT * FROM anime WHERE titleEnglish LIKE '%' || :q || '%' OR titleRomaji LIKE '%' || :q || '%' " +
            "OR titleNative LIKE '%' || :q || '%' ORDER BY popularity DESC LIMIT 60"
    )
    suspend fun searchLocal(q: String): List<AnimeEntity>

    @Query("SELECT * FROM anime ORDER BY popularity DESC LIMIT :limit")
    suspend fun topCached(limit: Int): List<AnimeEntity>

    @Query("SELECT * FROM character WHERE id = :id")
    fun observeCharacter(id: Int): Flow<CharacterEntity?>

    @Query("SELECT * FROM character WHERE id = :id")
    suspend fun getCharacter(id: Int): CharacterEntity?

    @Upsert suspend fun upsertCharacter(item: CharacterEntity)

    @Query("SELECT * FROM staff WHERE id = :id")
    fun observeStaffMember(id: Int): Flow<StaffEntity?>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getStaffMember(id: Int): StaffEntity?

    @Upsert suspend fun upsertStaffMember(item: StaffEntity)

    /** Cached rows nobody references any more (not in the library, favourites, feeds or schedule). */
    @Query(
        "DELETE FROM anime WHERE id NOT IN (SELECT animeId FROM library) AND id NOT IN (SELECT animeId FROM favorite_anime) " +
            "AND id NOT IN (SELECT animeId FROM feed_item) AND id NOT IN (SELECT animeId FROM airing) " +
            "AND id NOT IN (SELECT animeId FROM watched_episode)"
    )
    suspend fun pruneUnreferenced(): Int
}

@Dao
interface LibraryDao {
    @Transaction
    @Query("SELECT * FROM library ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LibraryItem>>

    @Transaction
    @Query("SELECT * FROM library")
    suspend fun all(): List<LibraryItem>

    @Query("SELECT * FROM library WHERE animeId = :animeId")
    fun observeEntry(animeId: Int): Flow<LibraryEntryEntity?>

    @Query("SELECT * FROM library WHERE animeId = :animeId")
    suspend fun get(animeId: Int): LibraryEntryEntity?

    @Upsert suspend fun upsert(entry: LibraryEntryEntity)
    @Upsert suspend fun upsertAll(entries: List<LibraryEntryEntity>)

    @Query("DELETE FROM library WHERE animeId = :animeId")
    suspend fun delete(animeId: Int)

    @Query("SELECT animeId FROM library WHERE status = 'WATCHING'")
    suspend fun watchingIds(): List<Int>

    @Query("SELECT episode FROM watched_episode WHERE animeId = :animeId ORDER BY episode")
    fun observeWatched(animeId: Int): Flow<List<Int>>

    @Query("SELECT episode FROM watched_episode WHERE animeId = :animeId ORDER BY episode")
    suspend fun watched(animeId: Int): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatched(items: List<WatchedEpisodeEntity>)

    @Query("DELETE FROM watched_episode WHERE animeId = :animeId AND episode = :episode")
    suspend fun deleteWatched(animeId: Int, episode: Int)

    @Query("DELETE FROM watched_episode WHERE animeId = :animeId AND episode > :above")
    suspend fun deleteWatchedAbove(animeId: Int, above: Int)

    @Query("DELETE FROM watched_episode WHERE animeId = :animeId")
    suspend fun deleteAllWatched(animeId: Int)

    @Query("SELECT MAX(episode) FROM watched_episode WHERE animeId = :animeId")
    suspend fun maxWatched(animeId: Int): Int?

    @Query("SELECT * FROM watched_episode")
    suspend fun allWatched(): List<WatchedEpisodeEntity>

    @Query(
        "SELECT anime.*, w.episode AS lastEpisode, w.watchedAt AS lastWatchedAt FROM watched_episode w " +
            "JOIN anime ON anime.id = w.animeId WHERE w.watchedAt = " +
            "(SELECT MAX(w2.watchedAt) FROM watched_episode w2 WHERE w2.animeId = w.animeId) " +
            "GROUP BY w.animeId ORDER BY w.watchedAt DESC LIMIT 100"
    )
    fun observeHistory(): Flow<List<HistoryRow>>

    @Query(
        "SELECT anime.*, f.addedAt AS favAddedAt FROM favorite_anime f JOIN anime ON anime.id = f.animeId " +
            "ORDER BY f.addedAt DESC"
    )
    fun observeFavoriteAnime(): Flow<List<FavoriteAnimeRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_anime WHERE animeId = :animeId)")
    fun observeIsFavoriteAnime(animeId: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteAnime(item: FavoriteAnimeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteAnimeAll(items: List<FavoriteAnimeEntity>)

    @Query("DELETE FROM favorite_anime WHERE animeId = :animeId")
    suspend fun deleteFavoriteAnime(animeId: Int)

    @Query("SELECT * FROM favorite_anime")
    suspend fun allFavoriteAnime(): List<FavoriteAnimeEntity>

    @Query("SELECT * FROM favorite_character ORDER BY addedAt DESC")
    fun observeFavoriteCharacters(): Flow<List<FavoriteCharacterEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_character WHERE characterId = :id)")
    fun observeIsFavoriteCharacter(id: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteCharacter(item: FavoriteCharacterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteCharacters(items: List<FavoriteCharacterEntity>)

    @Query("DELETE FROM favorite_character WHERE characterId = :id")
    suspend fun deleteFavoriteCharacter(id: Int)

    @Query("SELECT * FROM favorite_character")
    suspend fun allFavoriteCharacters(): List<FavoriteCharacterEntity>

    @Query("SELECT COUNT(*) FROM favorite_anime")
    fun observeFavoriteAnimeCount(): Flow<Int>

    @Query("DELETE FROM library")
    suspend fun clearLibrary()

    @Query("DELETE FROM watched_episode")
    suspend fun clearWatched()

    @Query("DELETE FROM favorite_anime")
    suspend fun clearFavoriteAnime()

    @Query("DELETE FROM favorite_character")
    suspend fun clearFavoriteCharacters()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_log ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notification_log WHERE `read` = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert
    suspend fun insert(item: NotificationEntity): Long

    @Query("SELECT * FROM notification_log ORDER BY createdAt DESC, id DESC")
    suspend fun all(): List<NotificationEntity>

    @Query("UPDATE notification_log SET `read` = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    @Query("UPDATE notification_log SET `read` = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notification_log")
    suspend fun clear()

    /** Keeps the history bounded (newest 300). */
    @Query("DELETE FROM notification_log WHERE id NOT IN (SELECT id FROM notification_log ORDER BY createdAt DESC, id DESC LIMIT 300)")
    suspend fun trim()
}
