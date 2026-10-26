package com.animeshadow.app.domain

enum class BrowseSort(val anilist: String) {
    POPULARITY("POPULARITY_DESC"),
    SCORE("SCORE_DESC"),
    TRENDING("TRENDING_DESC"),
    NEWEST("ID_DESC"),
    OLDEST("ID"),
    NAME("TITLE_ROMAJI"),
    YEAR_DESC("START_DATE_DESC"),
    YEAR_ASC("START_DATE"),
}

/** Remote catalogue query (AniList). Empty fields are not sent. */
data class BrowseQuery(
    val search: String = "",
    val sort: BrowseSort = BrowseSort.POPULARITY,
    val status: String? = null,
    val format: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val genre: String? = null,
    /** Age filter (v1.1): false = hide 18+ (default, as v1.0), true = 18+ only, null = everything. AniList isAdult. */
    val adult: Boolean? = false,
) {
    val feedKey: String
        get() = listOf("browse", search.trim().lowercase(), sort.name, status, format, season, year, genre, adult).joinToString("|")

    val activeFilterCount: Int get() = listOfNotNull(status, format, season, year, genre).size + (if (adult != false) 1 else 0)

    companion object {
        val SEASONS = listOf("WINTER", "SPRING", "SUMMER", "FALL")
        val GENRES = listOf(
            "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo", "Mecha", "Music",
            "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller",
        )
    }
}
