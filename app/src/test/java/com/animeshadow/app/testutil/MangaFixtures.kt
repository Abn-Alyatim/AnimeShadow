package com.animeshadow.app.testutil

import com.animeshadow.app.manga.data.DownloadEntity
import com.animeshadow.app.manga.data.DownloadState
import com.animeshadow.app.manga.data.MangaEntity

/** Manga fixtures built from the real titles/posters already in test resources (their images load offline). */
object MangaFixtures {
    private val origins = listOf("KR", "JP", "CN", "JP", "KR", "JP", "CN", "JP", "KR")
    private val statuses = listOf("RELEASING", "FINISHED", "RELEASING", "FINISHED", "HIATUS", "RELEASING", "FINISHED", "RELEASING", "FINISHED")

    val list: List<MangaEntity> by lazy {
        (Fixtures.top + Fixtures.season).distinctBy { it.id }.take(15).mapIndexed { i, a ->
            MangaEntity(
                id = 900_000 + a.id, titleRomaji = a.titleRomaji, titleEnglish = a.titleEnglish, titleNative = a.titleNative,
                cover = a.gridPoster, coverXL = a.gridPoster, banner = a.banner, color = a.color, format = "MANGA",
                status = statuses[i % statuses.size], country = origins[i % origins.size], chapters = if (i % 2 == 0) null else 120 + i * 7,
                volumes = if (i % 2 == 0) null else 12 + i, score = a.score, popularity = 1000, genres = a.genres,
                description = a.description, descriptionAr = a.descriptionAr, source = "WEB_NOVEL",
                startDate = "2018-03-04", endDate = null, detailLoaded = true, cachedAt = Fixtures.NOW,
            )
        }
    }

    val manhwa: MangaEntity get() = list.first { it.country == "KR" }.copy(
        descriptionAr = Fixtures.ONE_PIECE_AR_SAMPLE.take(420),
    )

    /** Page images for the reader shots (real poster images, served offline by TestImages). */
    val pages: List<String> by lazy { (Fixtures.top + Fixtures.airing).mapNotNull { it.gridPoster }.distinct().take(12) }

    fun downloads(m: MangaEntity, numbers: List<Int>, read: Set<Int> = emptySet()) = numbers.map { n ->
        DownloadEntity(
            chapterKey = "demo:${m.id}-$n", mangaId = m.id, providerId = "demo", chapterId = "${m.id}-$n", chapterNumber = n.toString(),
            state = DownloadState.DONE, totalPages = 17, donePages = 17, position = n, createdAt = Fixtures.NOW, finishedAt = Fixtures.NOW, read = n in read,
        )
    }
}
