package com.animeshadow.app.data

import com.animeshadow.app.data.local.AnimeCharacterEntity
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.AnimeStaffEntity
import com.animeshadow.app.data.local.CharacterEntity
import com.animeshadow.app.data.local.StaffEntity
import com.animeshadow.app.data.remote.CharacterDto
import com.animeshadow.app.data.remote.MediaDto
import com.animeshadow.app.data.remote.StaffDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val AppJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; isLenient = true }

@Serializable data class ScoreBucket(val score: Int, val amount: Int)
@Serializable data class StatusBucket(val status: String, val amount: Int)

@Serializable
data class Appearance(
    val animeId: Int,
    val title: String,
    val cover: String? = null,
    val role: String? = null,
    val vaId: Int? = null,
    val vaName: String? = null,
    val vaImage: String? = null,
)

@Serializable
data class VoiceRole(
    val animeId: Int,
    val animeTitle: String,
    val cover: String? = null,
    val characterId: Int? = null,
    val characterName: String? = null,
    val characterImage: String? = null,
    val role: String? = null,
)

private fun MediaDto.titleText(): String =
    title?.english ?: title?.romaji ?: title?.native ?: "#$id"

/** Card fields from any AniList media node. */
fun MediaDto.toCardEntity(now: Long): AnimeEntity = AnimeEntity(
    id = id,
    idMal = idMal,
    titleRomaji = title?.romaji,
    titleEnglish = title?.english,
    titleNative = title?.native,
    coverLarge = coverImage?.large,
    coverXL = coverImage?.extraLarge ?: coverImage?.large,
    color = coverImage?.color,
    format = format,
    status = status,
    episodes = episodes,
    duration = duration,
    season = season,
    seasonYear = seasonYear,
    score = averageScore ?: meanScore,
    popularity = popularity,
    genres = genres.joinToString("|"),
    studio = studios?.nodes?.firstOrNull()?.name,
    nextEpisode = nextAiringEpisode?.episode,
    nextAiringAt = nextAiringEpisode?.airingAt?.times(1000),
    cardUpdatedAt = now,
    isAdult = isAdult ?: false,
    description = description?.let(TextClean::clean),
    charNames = mainNames(),
)

/** Main character names (for the translator to keep as written), "|"-joined; null when the query had none. */
fun MediaDto.mainNames(): String? =
    characters?.edges?.mapNotNull { it.node?.name?.full?.trim()?.takeIf(String::isNotEmpty) }?.take(8)?.takeIf { it.isNotEmpty() }?.joinToString("|")

/** Full row from the details query; keeps the Jikan enrichment of the previous row. */
fun MediaDto.toDetailEntity(now: Long, old: AnimeEntity?): AnimeEntity = toCardEntity(now).copy(
    banner = bannerImage,
    description = description?.let(TextClean::clean),
    source = source,
    startDate = startDate?.iso(),
    endDate = endDate?.iso(),
    trailerId = trailer?.id,
    trailerSite = trailer?.site,
    trailerThumb = trailer?.thumbnail,
    scoreDist = stats?.let { s -> AppJson.encodeToString(s.scoreDistribution.map { ScoreBucket(it.score, it.amount) }) },
    statusDist = stats?.let { s -> AppJson.encodeToString(s.statusDistribution.map { StatusBucket(it.status, it.amount) }) },
    episodeTitles = streamingEpisodes.takeIf { it.isNotEmpty() }?.let { eps -> AppJson.encodeToString(eps.map { it.title.orEmpty() }) },
    malScore = old?.malScore,
    malMembers = old?.malMembers,
    detailFetchedAt = now,
    malPoster = old?.malPoster,
    malRating = old?.malRating,
    malScoredBy = old?.malScoredBy,
    malSource = old?.malSource,
    malFetchedAt = old?.malFetchedAt,
    // A changed English synopsis drops the old Arabic so the background translator picks the row up again.
    descriptionAr = old?.descriptionAr?.takeIf { old.description == description?.let(TextClean::clean) },
    descriptionArSrc = old?.descriptionArSrc,
    charNames = mainNames() ?: old?.charNames,
    arFailedAt = old?.arFailedAt,
)

fun MediaDto.characterRows(): List<AnimeCharacterEntity> = characters?.edges.orEmpty().mapIndexedNotNull { i, e ->
    val n = e.node ?: return@mapIndexedNotNull null
    val va = e.voiceActors.firstOrNull()
    AnimeCharacterEntity(
        animeId = id, characterId = n.id, name = n.name?.full ?: n.name?.native ?: "#${n.id}",
        nameNative = n.name?.native, image = n.image?.large, role = e.role,
        vaId = va?.id, vaName = va?.name?.full, vaImage = va?.image?.large, position = i,
    )
}

fun MediaDto.staffRows(): List<AnimeStaffEntity> = staff?.edges.orEmpty().mapIndexedNotNull { i, e ->
    val n = e.node ?: return@mapIndexedNotNull null
    AnimeStaffEntity(id, n.id, n.name?.full ?: "#${n.id}", n.image?.large, e.role ?: "", i)
}

fun CharacterDto.toEntity(now: Long): CharacterEntity = CharacterEntity(
    id = id,
    name = name?.full ?: name?.native ?: "#$id",
    nameNative = name?.native,
    image = image?.large,
    description = description?.let(TextClean::clean),
    favourites = favourites,
    appearancesJson = AppJson.encodeToString(
        media?.edges.orEmpty().mapNotNull { e ->
            val m = e.node ?: return@mapNotNull null
            val va = e.voiceActors.firstOrNull()
            Appearance(m.id, m.titleText(), m.coverImage?.large, e.characterRole, va?.id, va?.name?.full, va?.image?.large)
        } + media?.nodes.orEmpty().map { m -> Appearance(m.id, m.titleText(), m.coverImage?.large) },
    ),
    fetchedAt = now,
)

fun StaffDto.toEntity(now: Long): StaffEntity = StaffEntity(
    id = id,
    name = name?.full ?: name?.native ?: "#$id",
    nameNative = name?.native,
    image = image?.large,
    description = description?.let(TextClean::clean),
    rolesJson = AppJson.encodeToString(
        characterMedia?.edges.orEmpty().mapNotNull { e ->
            val m = e.node ?: return@mapNotNull null
            if (m.type != null && m.type != "ANIME") return@mapNotNull null
            val c = e.characters.firstOrNull()
            VoiceRole(m.id, m.titleText(), m.coverImage?.large, c?.id, c?.name?.full, c?.image?.large, e.characterRole)
        },
    ),
    fetchedAt = now,
)

object TextClean {
    private val spoiler = Regex("~!.*?!~", RegexOption.DOT_MATCHES_ALL)
    private val br = Regex("(?i)<br\\s*/?>")
    private val tag = Regex("<[^>]+>")
    private val link = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val bold = Regex("__(.+?)__|\\*\\*(.+?)\\*\\*")
    private val italic = Regex("(?<![_\\w])_([^_\\n]+)_(?![_\\w])")
    private val blankLines = Regex("\\n{3,}")

    fun clean(raw: String): String {
        var s = raw.replace(spoiler, "")
        s = s.replace(br, "\n").replace(tag, "")
        s = s.replace(link) { it.groupValues[1] }
        s = s.replace(bold) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        s = s.replace(italic) { it.groupValues[1] }
        s = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
        return s.replace(blankLines, "\n\n").trim()
    }
}
