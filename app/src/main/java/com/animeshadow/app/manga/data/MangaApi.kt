package com.animeshadow.app.manga.data

import com.animeshadow.app.data.remote.CoverDto
import com.animeshadow.app.data.remote.FuzzyDateDto
import com.animeshadow.app.data.remote.GqlError
import com.animeshadow.app.data.remote.GqlRequest
import com.animeshadow.app.data.remote.PageInfoDto
import com.animeshadow.app.data.remote.TitleDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import retrofit2.http.Body
import retrofit2.http.POST

/** AniList GraphQL, type MANGA (same endpoint and polite limiter as the anime side). */
interface MangaApi {
    @POST("/") suspend fun page(@Body request: GqlRequest): MangaPageResponse
    @POST("/") suspend fun media(@Body request: GqlRequest): MangaMediaResponse
}

@Serializable data class MangaPageResponse(val data: MangaPageData? = null, val errors: List<GqlError>? = null)
@Serializable data class MangaPageData(@SerialName("Page") val page: MangaPageDto? = null)
@Serializable data class MangaPageDto(val pageInfo: PageInfoDto? = null, val media: List<MangaMediaDto> = emptyList())
@Serializable data class MangaMediaResponse(val data: MangaMediaData? = null, val errors: List<GqlError>? = null)
@Serializable data class MangaMediaData(@SerialName("Media") val media: MangaMediaDto? = null)

@Serializable
data class MangaMediaDto(
    val id: Int,
    val title: TitleDto? = null,
    val coverImage: CoverDto? = null,
    val bannerImage: String? = null,
    val format: String? = null,
    val status: String? = null,
    val countryOfOrigin: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val genres: List<String>? = null,
    val isAdult: Boolean? = null,
    val description: String? = null,
    val source: String? = null,
    val startDate: FuzzyDateDto? = null,
    val endDate: FuzzyDateDto? = null,
    // v1.3.1 details: MAL id (MAL cell), authors (info grid), relations («ذات صلة» with السابق / التالي).
    val idMal: Int? = null,
    val staff: MangaStaffConn? = null,
    val relations: MangaRelConn? = null,
) {
    fun toEntity(now: Long, detail: Boolean): MangaEntity = MangaEntity(
        id = id,
        titleRomaji = title?.romaji,
        titleEnglish = title?.english,
        titleNative = title?.native,
        cover = coverImage?.large,
        coverXL = coverImage?.extraLarge,
        banner = bannerImage,
        color = coverImage?.color,
        format = format,
        status = status,
        country = countryOfOrigin,
        chapters = chapters,
        volumes = volumes,
        score = averageScore,
        popularity = popularity,
        genres = genres.orEmpty().joinToString("|"),
        description = description?.let(::cleanDescription),
        source = source,
        startDate = startDate?.iso(),
        endDate = endDate?.iso(),
        isAdult = isAdult == true,
        detailLoaded = detail,
        cachedAt = now,
        idMal = idMal,
        authors = staff?.edges.orEmpty()
            .filter { e -> e.role.orEmpty().let { it.contains("Story", true) || it.contains("Art", true) || it.contains("Original", true) } }
            .mapNotNull { it.node?.name?.full }.distinct().take(3).joinToString("|").ifBlank { null },
        relationsJson = relations?.let { r -> MangaRelation.encode(r.edges.mapNotNull { it.toRelation() }) },
    )
}

@Serializable data class MangaStaffConn(val edges: List<MangaStaffEdge> = emptyList())
@Serializable data class MangaStaffEdge(val role: String? = null, val node: MangaStaffNode? = null)
@Serializable data class MangaStaffNode(val name: MangaStaffName? = null)
@Serializable data class MangaStaffName(val full: String? = null)
@Serializable data class MangaRelConn(val edges: List<MangaRelEdge> = emptyList())
@Serializable
data class MangaRelEdge(val relationType: String? = null, val node: MangaRelNode? = null) {
    fun toRelation(): MangaRelation? {
        val n = node ?: return null
        val t = n.title?.english ?: n.title?.romaji ?: n.title?.native ?: return null
        return MangaRelation(n.id, relationType ?: "OTHER", n.type ?: "MANGA", n.format, t, n.coverImage?.extraLarge ?: n.coverImage?.large, n.coverImage?.color, n.averageScore)
    }
}
@Serializable
data class MangaRelNode(val id: Int, val type: String? = null, val format: String? = null, val title: TitleDto? = null, val coverImage: CoverDto? = null, val averageScore: Int? = null)

/** One related title of a manga (stored as JSON on the manga row; only MANGA-type ones open a page in manga mode). */
@Serializable
data class MangaRelation(
    val id: Int,
    val relationType: String,
    val type: String,
    val format: String? = null,
    val title: String,
    val cover: String? = null,
    val color: String? = null,
    val score: Int? = null,
) {
    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        fun encode(list: List<MangaRelation>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(serializer()), list)
        fun decode(s: String?): List<MangaRelation> = if (s.isNullOrBlank()) emptyList() else
            runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(serializer()), s) }.getOrDefault(emptyList())
        /** السابق first, then التالي, then the rest; manga only (anime has no page in manga mode). */
        fun forPage(list: List<MangaRelation>): List<MangaRelation> = list.filter { it.type == "MANGA" }
            .sortedBy { when (it.relationType) { "PREQUEL" -> 0; "SEQUEL" -> 1; else -> 2 } }
    }
}

/** AniList descriptions carry light HTML (<br>, <i>) and a "(Source: …)" tail. */
fun cleanDescription(raw: String): String = raw
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

object MangaQueries {
    private const val CARD = "id title{romaji english native} coverImage{large extraLarge color} bannerImage format status countryOfOrigin chapters volumes averageScore popularity genres isAdult"

    val PAGE = """
        query(${'$'}page:Int,${'$'}perPage:Int,${'$'}sort:[MediaSort],${'$'}country:CountryCode,${'$'}genre:String,${'$'}search:String){
          Page(page:${'$'}page,perPage:${'$'}perPage){
            pageInfo{hasNextPage currentPage}
            media(type:MANGA,isAdult:false,sort:${'$'}sort,countryOfOrigin:${'$'}country,genre:${'$'}genre,search:${'$'}search){ $CARD }
          }
        }
    """.trimIndent()

    val DETAIL = """
        query(${'$'}id:Int){ Media(id:${'$'}id,type:MANGA){ $CARD description(asHtml:false) source startDate{year month day} endDate{year month day} idMal staff(perPage:8,sort:RELEVANCE){edges{role node{name{full}}}} relations{edges{relationType node{id type format title{romaji english native} coverImage{large extraLarge color} averageScore}}} } }
    """.trimIndent()

    fun page(page: Int, sort: String, country: String?, genre: String? = null, search: String? = null, perPage: Int = 30) = GqlRequest(
        PAGE,
        buildJsonObject {
            put("page", page)
            put("perPage", perPage)
            putJsonArray("sort") { add(kotlinx.serialization.json.JsonPrimitive(sort)) }
            if (country != null) put("country", country)
            if (genre != null) put("genre", genre)
            if (!search.isNullOrBlank()) put("search", search)
        },
    )

    fun detail(id: Int) = GqlRequest(DETAIL, buildJsonObject { put("id", id) })

    val empty: JsonObject get() = buildJsonObject { }
}
