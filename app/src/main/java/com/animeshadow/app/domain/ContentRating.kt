package com.animeshadow.app.domain

/**
 * Age rating shown on details ("مسلسل | 13+") and used by the search filter.
 * Source order: MyAnimeList rating (Jikan or the MAL page), else AniList isAdult -> 18+.
 */
enum class AgeRating(val label: String) { ALL("G"), CHILDREN("PG"), TEEN("13+"), MATURE("17+"), ADULT("18+") }

object ContentRating {
    /** Maps MAL's rating text ("PG-13 - Teens 13 or older", "R - 17+ (violence & profanity)", "R+ - Mild Nudity", "Rx - Hentai"). */
    fun fromMal(rating: String?): AgeRating? {
        val r = rating?.trim()?.lowercase() ?: return null
        return when {
            r.startsWith("rx") -> AgeRating.ADULT
            r.startsWith("r+") -> AgeRating.MATURE
            r.startsWith("r") -> AgeRating.MATURE
            r.startsWith("pg-13") || r.startsWith("pg13") -> AgeRating.TEEN
            r.startsWith("pg") -> AgeRating.CHILDREN
            r.startsWith("g") -> AgeRating.ALL
            else -> null
        }
    }

    fun of(malRating: String?, isAdult: Boolean): AgeRating? = fromMal(malRating) ?: if (isAdult) AgeRating.ADULT else null
}
