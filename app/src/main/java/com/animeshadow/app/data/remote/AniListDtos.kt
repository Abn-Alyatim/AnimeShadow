package com.animeshadow.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GqlRequest(val query: String, val variables: JsonObject)

@Serializable
data class GqlError(val message: String? = null, val status: Int? = null)

@Serializable
data class PageResponse(val data: PageData? = null, val errors: List<GqlError>? = null)

@Serializable
data class PageData(@SerialName("Page") val page: PageDto? = null)

@Serializable
data class PageInfoDto(val hasNextPage: Boolean = false, val currentPage: Int? = null)

@Serializable
data class PageDto(
    val pageInfo: PageInfoDto? = null,
    val media: List<MediaDto> = emptyList(),
    val airingSchedules: List<AiringScheduleDto> = emptyList(),
    val characters: List<CharacterDto> = emptyList(),
)

@Serializable
data class MediaResponse(val data: MediaData? = null, val errors: List<GqlError>? = null)

@Serializable
data class MediaData(@SerialName("Media") val media: MediaDto? = null)

@Serializable
data class CharacterResponse(val data: CharacterData? = null, val errors: List<GqlError>? = null)

@Serializable
data class CharacterData(@SerialName("Character") val character: CharacterDto? = null)

@Serializable
data class StudioResponse(val data: StudioData? = null, val errors: List<GqlError>? = null)

@Serializable
data class StudioData(@SerialName("Studio") val studio: StudioPageDto? = null)

@Serializable
data class StudioMediaDto(val pageInfo: PageInfoDto? = null, val nodes: List<MediaDto> = emptyList())

@Serializable
data class StudioPageDto(val id: Int, val name: String? = null, val media: StudioMediaDto? = null)

@Serializable
data class StaffResponse(val data: StaffData? = null, val errors: List<GqlError>? = null)

@Serializable
data class StaffData(@SerialName("Staff") val staff: StaffDto? = null)

@Serializable
data class TitleDto(val romaji: String? = null, val english: String? = null, val native: String? = null)

@Serializable
data class CoverDto(val large: String? = null, val extraLarge: String? = null, val color: String? = null)

@Serializable
data class NextAiringDto(val episode: Int, val airingAt: Long, val timeUntilAiring: Long? = null)

@Serializable
data class FuzzyDateDto(val year: Int? = null, val month: Int? = null, val day: Int? = null) {
    fun iso(): String? = year?.let { y ->
        buildString {
            append(y)
            if (month != null) {
                append('-').append(month.toString().padStart(2, '0'))
                if (day != null) append('-').append(day.toString().padStart(2, '0'))
            }
        }
    }
}

@Serializable
data class StudioDto(val id: Int? = null, val name: String)

@Serializable
data class StudioConnDto(val nodes: List<StudioDto> = emptyList())

@Serializable
data class TrailerDto(val id: String? = null, val site: String? = null, val thumbnail: String? = null)

@Serializable
data class RelationEdgeDto(val relationType: String? = null, val node: MediaDto? = null)

@Serializable
data class RelationConnDto(val edges: List<RelationEdgeDto> = emptyList())

@Serializable
data class RecNodeDto(val rating: Int? = null, val mediaRecommendation: MediaDto? = null)

@Serializable
data class RecConnDto(val nodes: List<RecNodeDto> = emptyList())

@Serializable
data class NameDto(val full: String? = null, val native: String? = null)

@Serializable
data class ImageDto(val large: String? = null)

@Serializable
data class PersonDto(val id: Int, val name: NameDto? = null, val image: ImageDto? = null)

@Serializable
data class CharacterEdgeDto(
    val role: String? = null,
    val node: PersonDto? = null,
    val voiceActors: List<PersonDto> = emptyList(),
)

@Serializable
data class CharacterConnDto(val edges: List<CharacterEdgeDto> = emptyList())

@Serializable
data class StaffEdgeDto(val role: String? = null, val node: PersonDto? = null)

@Serializable
data class StaffConnDto(val edges: List<StaffEdgeDto> = emptyList())

@Serializable
data class ScoreBucketDto(val score: Int, val amount: Int)

@Serializable
data class StatusBucketDto(val status: String, val amount: Int)

@Serializable
data class StatsDto(
    val scoreDistribution: List<ScoreBucketDto> = emptyList(),
    val statusDistribution: List<StatusBucketDto> = emptyList(),
)

@Serializable
data class StreamingEpisodeDto(val title: String? = null, val thumbnail: String? = null)

@Serializable
data class MediaDto(
    val id: Int,
    val idMal: Int? = null,
    val type: String? = null,
    val title: TitleDto? = null,
    val coverImage: CoverDto? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val averageScore: Int? = null,
    val meanScore: Int? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val studios: StudioConnDto? = null,
    val nextAiringEpisode: NextAiringDto? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val source: String? = null,
    val startDate: FuzzyDateDto? = null,
    val endDate: FuzzyDateDto? = null,
    val isAdult: Boolean? = null,
    val favourites: Int? = null,
    val trailer: TrailerDto? = null,
    val relations: RelationConnDto? = null,
    val recommendations: RecConnDto? = null,
    val characters: CharacterConnDto? = null,
    val staff: StaffConnDto? = null,
    val stats: StatsDto? = null,
    val streamingEpisodes: List<StreamingEpisodeDto> = emptyList(),
)

@Serializable
data class AiringScheduleDto(val episode: Int, val airingAt: Long, val media: MediaDto? = null)

@Serializable
data class MediaNodesDto(val nodes: List<MediaDto> = emptyList())

@Serializable
data class CharacterMediaEdgeDto(
    val characterRole: String? = null,
    val node: MediaDto? = null,
    val voiceActors: List<PersonDto> = emptyList(),
)

@Serializable
data class CharacterMediaConnDto(val edges: List<CharacterMediaEdgeDto> = emptyList(), val nodes: List<MediaDto> = emptyList())

@Serializable
data class CharacterDto(
    val id: Int,
    val name: NameDto? = null,
    val image: ImageDto? = null,
    val description: String? = null,
    val favourites: Int? = null,
    val gender: String? = null,
    val age: String? = null,
    val media: CharacterMediaConnDto? = null,
)

@Serializable
data class StaffCharacterMediaEdgeDto(
    val characterRole: String? = null,
    val node: MediaDto? = null,
    val characters: List<PersonDto> = emptyList(),
)

@Serializable
data class StaffCharacterMediaConnDto(val edges: List<StaffCharacterMediaEdgeDto> = emptyList())

@Serializable
data class StaffDto(
    val id: Int,
    val name: NameDto? = null,
    val image: ImageDto? = null,
    val description: String? = null,
    val primaryOccupations: List<String> = emptyList(),
    val characterMedia: StaffCharacterMediaConnDto? = null,
)

// ---------- v1.2: AniList login ----------
@Serializable
data class ViewerResponse(val data: ViewerData? = null, val errors: List<GqlError>? = null)

@Serializable
data class ViewerData(@SerialName("Viewer") val viewer: ViewerDto? = null)

@Serializable
data class ViewerDto(val id: Int, val name: String, val avatar: ViewerAvatar? = null)

@Serializable
data class ViewerAvatar(val large: String? = null, val medium: String? = null)
