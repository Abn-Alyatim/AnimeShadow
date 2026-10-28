package com.animeshadow.app.manga.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.animeshadow.app.MainActivity
import com.animeshadow.app.R
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.AppJson
import com.animeshadow.app.manga.data.DownloadEntity
import com.animeshadow.app.manga.data.DownloadState
import com.animeshadow.app.manga.data.MangaDatabase
import com.animeshadow.app.manga.data.MangaEntity
import com.animeshadow.app.manga.source.ChapterPages
import com.animeshadow.app.manga.source.ChapterProvider
import com.animeshadow.app.manga.source.ChapterProviders
import com.animeshadow.app.manga.source.ProviderChapter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Starts / stops the background download job. Faked in unit tests. */
interface DownloadRunner {
    fun start()
    fun stop()
}

class WorkManagerDownloadRunner(private val context: Context) : DownloadRunner {
    override fun start() = DownloadScheduler.start(context)
    override fun stop() = DownloadScheduler.stop(context)
}

/**
 * Download queue («قائمة التنزيل») and the downloaded library («المانجات المحملة»). One chapter at a time in the
 * user's order, its pages [ChapterDownloader.DEFAULT_PARALLEL] at a time. Everything is in manga.db + filesDir/manga.
 */
class MangaDownloads(
    private val db: MangaDatabase,
    private val root: File,
    private val downloader: ChapterDownloader,
    private val clock: Clock,
    private val runner: DownloadRunner,
    private val provider: () -> ChapterProvider = { ChapterProviders.active },
) {
    private val dao get() = db.downloads()

    fun observeQueue() = dao.observeQueue()
    fun observeDownloadedTitles() = dao.observeDownloadedTitles()
    fun observeDownloadedChapters(mangaId: Int) = dao.observeDownloadedChapters(mangaId)
    fun observeTitleDownloads(mangaId: Int) = dao.observeTitleDownloads(mangaId)
    fun observeActiveCount() = dao.observeActiveCount()

    fun chapterDir(mangaId: Int, chapterKey: String) = File(root, "$mangaId/" + chapterKey.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    /** Pages saved on the device for a finished (or partly finished) chapter, in order. */
    fun localPages(mangaId: Int, chapterKey: String): List<File> = ChapterDownloader.localPages(chapterDir(mangaId, chapterKey))

    suspend fun enqueue(manga: MangaEntity, chapter: ProviderChapter, providerId: String = provider().id) {
        val key = ChapterProviders.chapterKey(providerId, chapter.id)
        val old = dao.get(key)
        if (old?.state == DownloadState.DONE) return
        // The title's details are already in manga.db (the title page stored them): the downloaded library shows them offline.
        db.manga().upsertKeepingDetails(listOf(manga))
        dao.upsert(
            (old ?: DownloadEntity(key, manga.id, providerId, chapter.id, chapter.number, chapter.title, createdAt = clock.now(), position = (dao.maxPosition() ?: -1) + 1))
                .copy(state = DownloadState.QUEUED, error = null),
        )
        runner.start()
    }

    /** While reading: queue the next chapter so it is ready (offline) when you get there. */
    suspend fun prefetchNext(manga: MangaEntity, chapters: List<ProviderChapter>, current: ProviderChapter) {
        val next = chapters.filter { it.numberValue > current.numberValue }.minByOrNull { it.numberValue } ?: return
        enqueue(manga, next)
    }

    suspend fun pauseAll() { dao.pauseAll(); runner.stop() }
    suspend fun resumeAll() { dao.resumeAll(); runner.start() }
    suspend fun pause(key: String) { dao.setState(key, DownloadState.PAUSED); if (dao.activeCount() == 0) runner.stop() }
    suspend fun resume(key: String) { dao.setState(key, DownloadState.QUEUED); runner.start() }

    suspend fun cancel(key: String) {
        val d = dao.get(key) ?: return
        dao.delete(key)
        withContext(Dispatchers.IO) { chapterDir(d.mangaId, key).deleteRecursively() }
        if (dao.activeCount() == 0) runner.stop()
    }

    suspend fun cancelAll() {
        runner.stop()
        val rows = dao.unfinished()
        dao.deleteUnfinished()
        withContext(Dispatchers.IO) { rows.forEach { chapterDir(it.mangaId, it.chapterKey).deleteRecursively() } }
    }

    /** Drag to reorder: [keys] in the new order. */
    suspend fun reorder(keys: List<String>) = keys.forEachIndexed { i, k -> dao.setPosition(k, i) }

    suspend fun moveToTop(key: String) {
        val q = dao.unfinished().sortedBy { it.position }.map { it.chapterKey }
        reorder(listOf(key) + (q - key))
    }

    suspend fun deleteChapter(key: String) = cancel(key)

    suspend fun setRead(key: String, read: Boolean) = dao.setRead(key, read)

    /**
     * The background job's loop: takes the first queued chapter, saves its page list, downloads the missing pages,
     * repeats. A network error leaves the chapter QUEUED with its finished pages and rethrows (the job retries when
     * the network is back). [onProgress] feeds the notification.
     */
    suspend fun runQueue(onProgress: suspend (DownloadEntity, Int, Int) -> Unit = { _, _, _ -> }) {
        dao.requeueRunning()
        while (true) {
            val d = dao.nextQueued() ?: return
            dao.setState(d.chapterKey, DownloadState.RUNNING)
            try {
                val pages = pagesOf(d)
                if (pages == null) {
                    dao.setState(d.chapterKey, DownloadState.FAILED, "provider")
                    continue
                }
                if (pages.urls.isEmpty()) {
                    dao.setState(d.chapterKey, DownloadState.FAILED, "empty")
                    continue
                }
                downloader.download(pages, chapterDir(d.mangaId, d.chapterKey)) { done, total ->
                    dao.progress(d.chapterKey, done, total)
                    onProgress(d, done, total)
                }
                dao.markDone(d.chapterKey, clock.now())
            } catch (c: CancellationException) {
                withContext(NonCancellable) {
                    // A pause already set PAUSED; anything else (job stopped by the system) goes back in the queue.
                    if (dao.get(d.chapterKey)?.state == DownloadState.RUNNING) dao.setState(d.chapterKey, DownloadState.QUEUED)
                }
                throw c
            } catch (e: IOException) {
                dao.setState(d.chapterKey, DownloadState.QUEUED, "network")
                throw e
            }
        }
    }

    private suspend fun pagesOf(d: DownloadEntity): ChapterPages? {
        d.pagesJson?.let { json -> runCatching { AppJson.decodeFromString(ChapterPages.serializer(), json) }.getOrNull()?.let { return it } }
        val p = ChapterProviders.byId(d.providerId)?.takeIf { it.isAvailable } ?: provider().takeIf { it.id == d.providerId && it.isAvailable } ?: return null
        val pages = p.pageUrls(ProviderChapter(d.chapterId, d.chapterNumber, d.chapterTitle))
        dao.setPages(d.chapterKey, AppJson.encodeToString(ChapterPages.serializer(), pages), pages.urls.size)
        return pages
    }
}

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloads: MangaDownloads,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { setForeground(DownloadNotifier.info(applicationContext, null, 0, 0)) }
        return try {
            var lastPost = 0L
            downloads.runQueue { d, done, total ->
                val now = System.currentTimeMillis()
                if (now - lastPost > 400 || done == total) {
                    lastPost = now
                    runCatching { setForeground(DownloadNotifier.info(applicationContext, d, done, total)) }
                }
            }
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = DownloadNotifier.info(applicationContext, null, 0, 0)
}

object DownloadScheduler {
    const val NAME = "manga_downloads"

    fun start(context: Context) {
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}

object DownloadNotifier {
    const val CHANNEL = "manga_downloads"
    private const val ID = 7_301

    fun info(context: Context, d: DownloadEntity?, done: Int, total: Int): ForegroundInfo {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.manga_dl_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            context, ID, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_as)
            .setContentTitle(context.getString(R.string.manga_dl_running))
            .setContentText(if (d == null) "" else context.getString(R.string.manga_chapter_n, d.chapterNumber) + "  " + "\u2066$done/$total\u2069")
            .setProgress(total.coerceAtLeast(0), done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(ID, n)
    }

    @Suppress("unused")
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
