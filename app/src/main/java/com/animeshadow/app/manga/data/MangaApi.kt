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
    )
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
        query(${'$'}id:Int){ Media(id:${'$'}id,type:MANGA){ $CARD description(asHtml:false) source startDate{year month day} endDate{year month day} } }
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
