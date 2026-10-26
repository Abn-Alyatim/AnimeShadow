package com.animeshadow.app.testutil

import com.animeshadow.app.data.AppJson
import com.animeshadow.app.data.characterRows
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.local.LibraryItem
import com.animeshadow.app.data.local.WatchStatus
import com.animeshadow.app.data.remote.CharacterResponse
import com.animeshadow.app.data.remote.MalPage
import com.animeshadow.app.data.remote.MediaResponse
import com.animeshadow.app.data.remote.PageResponse
import com.animeshadow.app.data.remote.StaffResponse
import com.animeshadow.app.data.staffRows
import com.animeshadow.app.data.toCardEntity
import com.animeshadow.app.data.toDetailEntity
import com.animeshadow.app.data.toEntity

/** Real responses recorded from graphql.anilist.co on 2026-10-26 (see README). */
object Fixtures {
    const val NOW = 1_793_000_000_000L // fixed clock for tests (2026-10-26)

    fun text(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResourceAsStream("fixtures/$name")) { "missing fixture $name" }.readBytes().toString(Charsets.UTF_8)

    fun page(name: String) = AppJson.decodeFromString<PageResponse>(text(name)).data!!.page!!
    val airing by lazy { page("anilist_airing.json").media.map { it.toCardEntity(NOW) } }
    val season by lazy { page("anilist_season.json").media.map { it.toCardEntity(NOW) } }
    val top by lazy { page("anilist_top.json").media.map { it.toCardEntity(NOW) } }
    val search by lazy { page("anilist_search.json").media.map { it.toCardEntity(NOW) } }
    val schedule by lazy { page("anilist_schedule.json").airingSchedules }
    val popularCharacters by lazy { page("anilist_popular_characters.json").characters.map { it.toEntity(NOW) } }
    val detailDto by lazy { AppJson.decodeFromString<MediaResponse>(text("anilist_detail.json")).data!!.media!! }
    val detail: AnimeEntity by lazy { detailDto.toDetailEntity(NOW, null) }
    val characters by lazy { detailDto.characterRows() }
    val staff by lazy { detailDto.staffRows() }
    val relations by lazy { detailDto.relations!!.edges.filter { it.node != null && it.node.type == "ANIME" }.map { it.node!!.toCardEntity(NOW) to (it.relationType ?: "OTHER") } }
    val recommendations by lazy { detailDto.recommendations!!.nodes.mapNotNull { it.mediaRecommendation?.toCardEntity(NOW) } }
    val character by lazy { AppJson.decodeFromString<CharacterResponse>(text("anilist_character.json")).data!!.character!!.toEntity(NOW) }
    val staffMember by lazy { AppJson.decodeFromString<StaffResponse>(text("anilist_staff.json")).data!!.staff!!.toEntity(NOW) }

    /** Recorded 2026-10-26: AniList details of ONE PIECE (ref 11 is One Piece) and its real MAL page. */
    val onePieceDto by lazy { AppJson.decodeFromString<MediaResponse>(text("anilist_detail_onepiece.json")).data!!.media!! }
    val onePieceMal by lazy { MalPage.parse(text("mal_page_21_trimmed.html"))!! }

    /**
     * Arabic of the first two paragraphs as the real translator (MyMemory, names kept) returned it on 2026-10-26,
     * used for the screenshots (unit tests do not call the network).
     */
    const val ONE_PIECE_AR_SAMPLE = "كان جولد روجر معروفًا باسم ملك القراصنة، وهو أقوى وأشهر كائن أبحر في الخط الكبير. أدى القبض على روجر وموته من قبل الحكومة العالمية إلى إحداث تغيير في جميع أنحاء العالم. كشفت كلماته الأخيرة قبل وفاته عن موقع أعظم كنز في العالم، One Piece. كان هذا الكشف هو الذي جلب العصر الكبير للقراصنة، الرجال الذين حلموا بالعثور على One Piece (الذي يعد بقدر غير محدود من الثروات والشهرة)، وربما أكثر الألقاب المرغوبة للشخص الذي وجدها، لقب ملك القراصنة.\nأدخل Monkey D. Luffy، صبي يبلغ من العمر 17 عامًا يتحدى تعريفك القياسي للقراصنة. بدلاً من الشخصية الشعبية للقراصنة الأشرار المتصلبين عديمي الأسنان الذين ينهبون القرى من أجل المتعة، فإن سبب لوفي لكونه قرصانًا هو عجب خالص ؛ إن فكرة المغامرة المثيرة ومقابلة أشخاص جدد ومثيرين للاهتمام، إلى جانب العثور على One Piece، هي أسبابه في أن يصبح قرصانًا. يسير على خطى بطل طفولته، Luffy وطاقمه يسافرون عبر الخط الكبير، ويختبرون مغامرات مجنونة، ويكشفون عن أسرار مظلمة ويقاتلون أعداء أقوياء، كل ذلك من أجل الوصول إلى One Piece."

    val onePiece: AnimeEntity by lazy {
        val m = onePieceMal
        onePieceDto.toDetailEntity(NOW, null).copy(
            malScore = m.score, malScoredBy = m.scoredBy, malMembers = m.members, malRating = m.rating, malPoster = m.poster,
            malSource = m.source, malFetchedAt = NOW,
        )
    }
    val onePieceTranslated: AnimeEntity by lazy {
        onePiece.copy(descriptionAr = ONE_PIECE_AR_SAMPLE, descriptionArSrc = onePiece.description.hashCode())
    }

    fun entry(anime: AnimeEntity, status: WatchStatus, progress: Int = 0, score: Int? = null, addedAt: Long = NOW) =
        LibraryItem(LibraryEntryEntity(anime.id, status, progress, score, addedAt, addedAt), anime)

    /** A realistic library built from real titles. */
    val library: List<LibraryItem> by lazy {
        val finished = (listOf(detail) + top + season).filter { it.status == "FINISHED" }.distinctBy { it.id }
        val airingPool = airing.filter { it.status == "RELEASING" }
        val statuses = listOf(WatchStatus.WATCHING, WatchStatus.COMPLETED, WatchStatus.PLAN_TO_WATCH, WatchStatus.COMPLETED, WatchStatus.ON_HOLD, WatchStatus.DROPPED)
        var f = 0
        var r = 0
        (0 until 18).map { i ->
            val st = statuses[i % statuses.size]
            val a = if (st == WatchStatus.COMPLETED || st == WatchStatus.DROPPED) finished[f++] else airingPool[r++]
            val prog = when (st) {
                WatchStatus.COMPLETED -> a.episodes ?: 12
                WatchStatus.WATCHING -> minOf(7 + i, a.episodes ?: 99)
                WatchStatus.ON_HOLD, WatchStatus.DROPPED -> 3
                else -> 0
            }
            entry(a, st, prog, if (st == WatchStatus.COMPLETED || st == WatchStatus.WATCHING) 6 + (i % 5) else null, NOW - i * 86_400_000L)
        }
    }
}
