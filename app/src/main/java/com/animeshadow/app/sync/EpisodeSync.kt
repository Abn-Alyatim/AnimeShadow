package com.animeshadow.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.animeshadow.app.MainActivity
import com.animeshadow.app.R
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.NotificationRepository
import com.animeshadow.app.data.local.AnimeEntity
import com.animeshadow.app.data.local.LibraryEntryEntity
import com.animeshadow.app.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

data class NewEpisode(val anime: AnimeEntity, val episode: Int)

/** Pure detection rule (unit tested): a followed anime has a newer released episode than the one last notified. */
object NewEpisodeDetector {
    fun detect(entries: List<LibraryEntryEntity>, fresh: List<AnimeEntity>): List<NewEpisode> {
        val byId = fresh.associateBy { it.id }
        return entries.mapNotNull { e ->
            val a = byId[e.animeId] ?: return@mapNotNull null
            val latest = a.latestEpisode ?: return@mapNotNull null
            val seen = e.lastSeenEpisode
            when {
                seen == null -> null // first sync after following: remember silently
                latest > seen -> NewEpisode(a, latest)
                else -> null
            }
        }
    }
}

/** Periodic, battery-friendly sync of followed (Watching) anime: one AniList request per 50 titles. */
@HiltWorker
class EpisodeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val anime: AnimeRepository,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val notifications: NotificationRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.current().notifications) return Result.success()
        val ids = library.watchingIds()
        if (ids.isEmpty()) return Result.success()
        val fresh = anime.refreshCards(ids).getOrElse { return if (runAttemptCount < 3) Result.retry() else Result.failure() }
        val entries = ids.mapNotNull { library.entry(it) }
        val news = NewEpisodeDetector.detect(entries, fresh)
        news.forEach {
            notifications.log(it.anime, it.episode, NotificationRepository.KIND_NEW_EPISODE)
            Notifier.newEpisode(applicationContext, it.anime, it.episode)
        }
        fresh.forEach { a -> a.latestEpisode?.let { library.markSeenEpisode(a.id, it) } }
        return Result.success()
    }
}

/** One-shot local reminder at the expected airing time of the next episode. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val notifications: NotificationRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.current().notifications) return Result.success()
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val episode = inputData.getInt(KEY_EPISODE, 0)
        val id = inputData.getInt(KEY_ID, 0)
        notifications.log(id, title, inputData.getString(KEY_IMAGE), episode, NotificationRepository.KIND_REMINDER)
        Notifier.reminder(applicationContext, id, title, episode)
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_EPISODE = "episode"
        const val KEY_ID = "id"
        const val KEY_IMAGE = "image"
    }
}

object SyncScheduler {
    private const val PERIODIC = "episode-sync"

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<EpisodeSyncWorker>(3, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleReminder(context: Context, anime: AnimeEntity, nowMs: Long) {
        val at = anime.nextAiringAt ?: return
        val episode = anime.nextEpisode ?: return
        val delay = (at - nowMs).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_TITLE to anime.displayTitle, ReminderWorker.KEY_EPISODE to episode, ReminderWorker.KEY_ID to anime.id, ReminderWorker.KEY_IMAGE to anime.gridPoster))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-${anime.id}", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelReminder(context: Context, animeId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder-$animeId")
    }
}

object Notifier {
    const val CHANNEL = "new_episodes"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ANIME_ID, id)
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_as)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // permission revoked between the check and the post
        }
    }

    fun newEpisode(context: Context, anime: AnimeEntity, episode: Int) =
        post(context, anime.id, anime.displayTitle, context.getString(R.string.notif_new_episode, episode.toString()))

    fun reminder(context: Context, id: Int, title: String, episode: Int) =
        post(context, id, title, context.getString(R.string.notif_reminder, episode.toString()))
}
