package com.animeshadow.app.manga.source

import kotlinx.serialization.Serializable

/**
 * v1.3 MANGA chapters come from a pluggable [ChapterProvider]. No legal provider is chosen yet, so the app ships
 * with [NoChapterProvider] only: the chapter lists show «قريباً» and nothing is fetched from anyone. A provider
 * is plugged in ONE place ([ChapterProviders.active], see docs/CHAPTER_PROVIDER.md) and every screen, the reader,
 * the downloader and the offline library work with it unchanged.
 *
 * Rule: only a source that has the rights to the pages (an official / licensed API, or public-domain works).
 * No scanlation or piracy sites.
 */
interface ChapterProvider {
    /** Stable id stored with every download ("none", "my_licensed_api", …). Never change it once released. */
    val id: String

    /** Arabic display name shown in the reader («المصدر: …»). */
    val displayName: String

    /** False → the UI shows «قريباً» instead of a chapter list. */
    val isAvailable: Boolean

    /**
     * Chapters of the AniList MANGA [aniListId] (title hints help providers that search by name). Newest first is
     * not required: the app sorts by [ProviderChapter.number].
     */
    suspend fun listChapters(aniListId: Int, titles: List<String>): List<ProviderChapter>

    /** Ordered page images of one chapter. Called once per download; the result is saved, so resume needs no call. */
    suspend fun pageUrls(chapter: ProviderChapter): ChapterPages
}

@Serializable
data class ProviderChapter(
    /** Provider-side id (opaque to the app). */
    val id: String,
    /** "12", "12.5" … — shown as «الفصل 12». */
    val number: String,
    val title: String? = null,
    val language: String? = null,
    val publishedAt: Long? = null,
) {
    val numberValue: Double get() = number.toDoubleOrNull() ?: 0.0
}

/** Page URLs plus any headers the image host needs (Referer, Authorization …). */
@Serializable
data class ChapterPages(val urls: List<String>, val headers: Map<String, String> = emptyMap())

/** The shipped provider: no source → no chapters, «قريباً». */
object NoChapterProvider : ChapterProvider {
    override val id = "none"
    override val displayName = "—"
    override val isAvailable = false
    override suspend fun listChapters(aniListId: Int, titles: List<String>): List<ProviderChapter> = emptyList()
    override suspend fun pageUrls(chapter: ProviderChapter): ChapterPages = ChapterPages(emptyList())
}

/** Where the active provider is chosen. Tests swap it; the app keeps [NoChapterProvider] until a legal one exists. */
object ChapterProviders {
    @Volatile var active: ChapterProvider = NoChapterProvider

    /** Downloads remember the provider that made them; an unknown id (provider removed) still opens offline. */
    fun byId(id: String): ChapterProvider? = active.takeIf { it.id == id }

    fun chapterKey(providerId: String, chapterId: String) = "$providerId:$chapterId"
}
