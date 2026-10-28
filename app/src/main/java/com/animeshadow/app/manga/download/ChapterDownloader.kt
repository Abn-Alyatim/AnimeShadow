package com.animeshadow.app.manga.download

import com.animeshadow.app.manga.source.ChapterPages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Some pages could not be fetched (network dropped, server error). The finished ones stay on disk for the resume. */
class PartialDownloadException(val done: Int, val total: Int, cause: Throwable?) :
    IOException("downloaded $done of $total pages", cause)

/**
 * The fast part (Madyan's main complaint about other apps): a chapter's pages are fetched [parallel] at a time
 * (default 5), each written to "NNN.page.part" and renamed when complete. A page file that exists is done, so a
 * resume after a dropped network, a pause or a killed app only fetches what is missing, from the page it stopped at.
 */
class ChapterDownloader(
    private val client: OkHttpClient,
    private val parallel: Int = DEFAULT_PARALLEL,
    private val retries: Int = 2,
    private val retryDelayMs: Long = 700,
) {
    suspend fun download(pages: ChapterPages, dir: File, onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> }): Int = coroutineScope {
        val total = pages.urls.size
        withContext(Dispatchers.IO) { dir.mkdirs(); dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() } }
        val done = AtomicInteger(pages.urls.indices.count { pageFile(dir, it).length() > 0 })
        val lock = Mutex()
        onProgress(done.get(), total)
        val gate = Semaphore(parallel.coerceIn(1, 8))
        var firstError: Throwable? = null
        pages.urls.mapIndexed { i, url ->
            async {
                val target = pageFile(dir, i)
                if (target.length() > 0) return@async true
                gate.withPermit {
                    try {
                        fetch(url, pages.headers, target)
                        val n = done.incrementAndGet()
                        lock.withLock { onProgress(n, total) }
                        true
                    } catch (c: CancellationException) {
                        throw c
                    } catch (e: IOException) {
                        lock.withLock { if (firstError == null) firstError = e }
                        false
                    }
                }
            }
        }.awaitAll()
        val n = done.get()
        if (n < total) throw PartialDownloadException(n, total, firstError)
        n
    }

    private suspend fun fetch(url: String, headers: Map<String, String>, target: File) {
        var last: IOException? = null
        repeat(retries + 1) { attempt ->
            try {
                fetchOnce(url, headers, target)
                return
            } catch (e: IOException) {
                last = e
                if (attempt < retries) delay(retryDelayMs * (attempt + 1))
            }
        }
        throw last ?: IOException("page failed")
    }

    private suspend fun fetchOnce(url: String, headers: Map<String, String>, target: File) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        val part = File(target.parentFile, target.name + ".part")
        runInterruptible {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val body = r.body ?: throw IOException("empty body")
                part.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
            }
        }
        if (part.length() <= 0L) { part.delete(); throw IOException("empty page") }
        if (!part.renameTo(target)) { part.delete(); throw IOException("rename failed") }
    }

    companion object {
        const val DEFAULT_PARALLEL = 5

        fun pageFile(dir: File, index: Int) = File(dir, String.format(java.util.Locale.US, "%03d.page", index))

        /** The offline reader's pages: every finished page file, in order. */
        fun localPages(dir: File): List<File> =
            dir.listFiles { f -> f.name.endsWith(".page") && f.length() > 0 }?.sortedBy { it.name }.orEmpty()
    }
}
