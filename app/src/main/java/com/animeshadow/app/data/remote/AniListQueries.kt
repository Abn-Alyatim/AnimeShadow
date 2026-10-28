package com.animeshadow.app.data.remote

/** GraphQL documents sent to graphql.anilist.co. Fields are the minimum each screen needs. */
object AniListQueries {
    const val CARD = "id idMal title{romaji english native} coverImage{large extraLarge color} format status " +
        "episodes duration season seasonYear averageScore meanScore popularity genres " +
        "studios(isMain:true){nodes{id name}} nextAiringEpisode{episode airingAt timeUntilAiring}"

    /**
     * v1.1.1: list rows also carry the English synopsis and the main names, so the background translator can have
     * the Arabic ready before a details page is opened (names are kept untranslated).
     */
    const val LIST_EXTRA = "description(asHtml:false) characters(perPage:4,sort:[ROLE,RELEVANCE,ID]){edges{node{id name{full}}}}"

    const val AIRING = "query(\$page:Int,\$perPage:Int){Page(page:\$page,perPage:\$perPage){pageInfo{hasNextPage currentPage} " +
        "media(type:ANIME,status:RELEASING,sort:POPULARITY_DESC,isAdult:false){$CARD $LIST_EXTRA}}}"

    const val SCHEDULE = "query(\$page:Int,\$from:Int,\$to:Int){Page(page:\$page,perPage:50){pageInfo{hasNextPage} " +
        "airingSchedules(airingAt_greater:\$from,airingAt_lesser:\$to,sort:TIME){episode airingAt media{$CARD isAdult}}}}"

    const val BROWSE = "query(\$page:Int,\$search:String,\$sort:[MediaSort],\$status:MediaStatus,\$format:MediaFormat," +
        "\$season:MediaSeason,\$year:Int,\$genre:String,\$tag:String,\$adult:Boolean){Page(page:\$page,perPage:30){pageInfo{hasNextPage} " +
        "media(type:ANIME,isAdult:\$adult,search:\$search,sort:\$sort,status:\$status,format:\$format,season:\$season," +
        "seasonYear:\$year,genre:\$genre,tag:\$tag){$CARD $LIST_EXTRA}}}"

    const val BY_IDS = "query(\$ids:[Int]){Page(page:1,perPage:50){media(type:ANIME,id_in:\$ids){$CARD $LIST_EXTRA}}}"

    /** Works of one studio (the studio chip on the details page), most popular first. */
    const val STUDIO = "query(\$search:String,\$page:Int){Studio(search:\$search){id name media(page:\$page,perPage:30,sort:POPULARITY_DESC,isMain:true)" +
        "{pageInfo{hasNextPage} nodes{$CARD $LIST_EXTRA}}}}"

    const val DETAIL = "query(\$id:Int){Media(id:\$id,type:ANIME){$CARD bannerImage description(asHtml:false) source " +
        "startDate{year month day} endDate{year month day} isAdult favourites trailer{id site thumbnail} " +
        "relations{edges{relationType node{$CARD type}}} " +
        "recommendations(perPage:12,sort:RATING_DESC){nodes{rating mediaRecommendation{$CARD}}} " +
        "characters(perPage:25,sort:[ROLE,RELEVANCE,ID]){edges{role node{id name{full native} image{large}} " +
        "voiceActors(language:JAPANESE,sort:[RELEVANCE,ID]){id name{full native} image{large}}}} " +
        "staff(perPage:12,sort:[RELEVANCE,ID]){edges{role node{id name{full native} image{large}}}} " +
        "stats{scoreDistribution{score amount} statusDistribution{status amount}} streamingEpisodes{title thumbnail}}}"

    const val CHARACTER = "query(\$id:Int){Character(id:\$id){id name{full native} image{large} description(asHtml:false) " +
        "favourites gender age media(perPage:10,sort:POPULARITY_DESC,type:ANIME){edges{characterRole node{$CARD} " +
        "voiceActors(language:JAPANESE){id name{full native} image{large}}}}}}"

    const val STAFF = "query(\$id:Int){Staff(id:\$id){id name{full native} image{large} description(asHtml:false) " +
        "primaryOccupations characterMedia(perPage:20,sort:POPULARITY_DESC){edges{characterRole " +
        "node{id title{romaji english} coverImage{large} type} characters{id name{full} image{large}}}}}}"

    const val POPULAR_CHARACTERS = "query(\$page:Int){Page(page:\$page,perPage:30){pageInfo{hasNextPage} " +
        "characters(sort:FAVOURITES_DESC){id name{full native} image{large} favourites " +
        "media(perPage:1,sort:POPULARITY_DESC){nodes{id title{romaji english}}}}}}"
}
