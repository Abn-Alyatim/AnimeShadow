# Plugging a chapter source into AnimeShadow (v1.3)

AnimeShadow ships **no chapter source**. Titles, posters, scores and descriptions come from AniList; the
«الفصول» tab shows «قريباً» until a *legal* source is plugged in (an official publisher API, your own server, or a
source you have the rights to). Never plug in a pirate site.

## The interface

`app/src/main/java/com/animeshadow/app/manga/source/ChapterProvider.kt`

```kotlin
interface ChapterProvider {
    val id: String            // short, stable, used in chapter keys "id:chapterId" (do not change it later)
    val displayName: String
    val isAvailable: Boolean  // false = the app shows «قريباً»
    suspend fun listChapters(aniListId: Int, titles: List<String>): List<ProviderChapter>
    suspend fun pageUrls(chapter: ProviderChapter): ChapterPages   // ordered page image URLs (+ optional headers)
}
```

* `listChapters` gets the AniList id and every known title (romaji / English / native) so you can match the work.
* `ProviderChapter(id, number, title?)` — `number` is shown as «الفصل-N» and used for sorting / «تابع الفصل N».
* `ChapterPages(urls, headers)` — headers (e.g. a Referer or an API token) are sent with every page request.

## Turning it on

1. Add a class, e.g. `manga/source/MyProvider.kt`, implementing `ChapterProvider` (use OkHttp/Retrofit like the rest of the app).
2. In `ChapterProviders` set `var active: ChapterProvider = MyProvider(...)` (or assign it in `AnimeShadowApp.onCreate`).
3. Build. Nothing else changes: the chapters tab, the reader, prefetch of the next chapter, the download queue
   (5 pages in parallel, resumable, foreground notification) and the offline library all use the provider.

## What the app does with it

* Downloads go to `filesDir/manga/<aniListId>/<chapterKey>/NNN.page` — a page that exists is never fetched again,
  so pause / network loss / app kill resume from the page they stopped at.
* The downloaded library reads `manga.db` only, so details and downloaded chapters open with no internet.
* Tests: `V13LogicTest` contains a `FakeProvider` against MockWebServer you can copy.
