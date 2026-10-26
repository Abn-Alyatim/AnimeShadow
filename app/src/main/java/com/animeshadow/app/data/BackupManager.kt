package com.animeshadow.app.data

import androidx.room.withTransaction
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.local.FavoriteAnimeEntity
import com.animeshadow.app.data.local.FavoriteCharacterEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.local.WatchedEpisodeEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class BackupAnime(
    val id: Int, val idMal: Int? = null, val title: String? = null, val titleRomaji: String? = null,
    val cover: String? = null, val format: String? = null, val status: String? = null, val episodes: Int? = null,
    val duration: Int? = null, val year: Int? = null, val score: Int? = null, val genres: String = "",
)

@Serializable
data class BackupEntry(
    val animeId: Int, val status: String, val progress: Int, val userScore: Int? = null,
    val addedAt: Long, val updatedAt: Long, val remind: Boolean = false,
    // v1.1 rating sliders (absent in v1.0 backups).
    val scoreStory: Int? = null, val scoreCharacters: Int? = null, val scoreAnimation: Int? = null, val scoreMusic: Int? = null,
)

@Serializable data class BackupEpisode(val animeId: Int, val episode: Int, val watchedAt: Long)
@Serializable data class BackupFavCharacter(val id: Int, val name: String, val image: String? = null, val animeId: Int? = null, val animeTitle: String? = null, val addedAt: Long)

@Serializable
data class BackupFile(
    val app: String = "ANIME SHADOW",
    val version: Int = 1,
    val exportedAt: Long,
    val anime: List<BackupAnime>,
    val library: List<BackupEntry>,
    val episodes: List<BackupEpisode>,
    val favoriteAnime: List<Int>,
    val favoriteAnimeAddedAt: List<Long> = emptyList(),
    val favoriteCharacters: List<BackupFavCharacter>,
)

data class ImportSummary(val library: Int, val episodes: Int, val favoriteAnime: Int, val favoriteCharacters: Int)

/** JSON backup of the user's own data (library, watched episodes, favourites) via Storage Access Framework streams. */
class BackupManager(private val db: AppDatabase) {
    suspend fun export(out: OutputStream, now: Long): BackupFile {
        val lib = db.libraryDao()
        val items = lib.all()
        val favs = lib.allFavoriteAnime()
        val ids = (items.map { it.entry.animeId } + favs.map { it.animeId }).distinct()
        val anime = if (ids.isEmpty()) emptyList() else ids.chunked(500).flatMap { db.animeDao().getByIds(it) }
        val file = BackupFile(
            exportedAt = now,
            anime = anime.map { BackupAnime(it.id, it.idMal, it.titleEnglish, it.titleRomaji, it.coverLarge, it.format, it.status, it.episodes, it.duration, it.seasonYear, it.score, it.genres) },
            library = items.map { e -> e.entry.let { BackupEntry(it.animeId, it.status.name, it.progress, it.userScore, it.addedAt, it.updatedAt, it.remind, it.scoreStory, it.scoreCharacters, it.scoreAnimation, it.scoreMusic) } },
            episodes = lib.allWatched().map { BackupEpisode(it.animeId, it.episode, it.watchedAt) },
            favoriteAnime = favs.map { it.animeId },
            favoriteAnimeAddedAt = favs.map { it.addedAt },
            favoriteCharacters = lib.allFavoriteCharacters().map { BackupFavCharacter(it.characterId, it.name, it.image, it.animeId, it.animeTitle, it.addedAt) },
        )
        out.write(AppJson.encodeToString(file).toByteArray(Charsets.UTF_8))
        out.flush()
        return file
    }

    /** Merges a backup into the current data (backup wins for the same anime). Rejects foreign/invalid files. */
    suspend fun import(input: InputStream): ImportSummary {
        val text = input.readBytes().toString(Charsets.UTF_8)
        val file = try {
            AppJson.decodeFromString<BackupFile>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("not an ANIME SHADOW backup", e)
        }
        require(file.app == "ANIME SHADOW") { "not an ANIME SHADOW backup" }
        val entries = file.library.mapNotNull { b ->
            val status = WatchStatus.entries.firstOrNull { it.name == b.status } ?: return@mapNotNull null
            LibraryEntryEntity(
                b.animeId, status, b.progress.coerceAtLeast(0), b.userScore?.coerceIn(1, 10), b.addedAt, b.updatedAt, remind = b.remind,
                scoreStory = b.scoreStory?.coerceIn(0, 10), scoreCharacters = b.scoreCharacters?.coerceIn(0, 10),
                scoreAnimation = b.scoreAnimation?.coerceIn(0, 10), scoreMusic = b.scoreMusic?.coerceIn(0, 10),
            )
        }
        db.withTransaction {
            val dao = db.animeDao()
            val existing = dao.getByIds(file.anime.map { it.id }).associateBy { it.id }
            dao.upsertAll(
                file.anime.filter { it.id !in existing }.map {
                    AnimeEntity(
                        id = it.id, idMal = it.idMal, titleEnglish = it.title, titleRomaji = it.titleRomaji, coverLarge = it.cover,
                        coverXL = it.cover, format = it.format, status = it.status, episodes = it.episodes, duration = it.duration,
                        seasonYear = it.year, score = it.score, genres = it.genres,
                    )
                },
            )
            val lib = db.libraryDao()
            lib.upsertAll(entries)
            lib.insertWatched(file.episodes.filter { it.episode >= 1 }.map { WatchedEpisodeEntity(it.animeId, it.episode, it.watchedAt) })
            lib.insertFavoriteAnimeAll(file.favoriteAnime.mapIndexed { i, id -> FavoriteAnimeEntity(id, file.favoriteAnimeAddedAt.getOrNull(i) ?: file.exportedAt) })
            lib.insertFavoriteCharacters(file.favoriteCharacters.map { FavoriteCharacterEntity(it.id, it.name, it.image, it.animeId, it.animeTitle, it.addedAt) })
        }
        return ImportSummary(entries.size, file.episodes.size, file.favoriteAnime.size, file.favoriteCharacters.size)
    }
}
