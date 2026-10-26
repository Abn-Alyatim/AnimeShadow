package com.animeshadow.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.translate.SynopsisPrefetcher
import com.animeshadow.app.data.translate.SynopsisTranslator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Runs [SynopsisPrefetcher] in the background, only with a network connection and never on the UI thread. */
@HiltWorker
class TranslateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val anime: AnimeRepository,
    private val translator: SynopsisTranslator,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val out = SynopsisPrefetcher(anime, translator).run(max = BATCH)
        when {
            out.quotaReached -> TranslateScheduler.kick(applicationContext, delayMinutes = 6 * 60)
            out.morePending && out.translated > 0 -> TranslateScheduler.kick(applicationContext, delayMinutes = 1, chain = true)
        }
        return Result.success()
    }

    companion object {
        const val BATCH = 25
    }
}

object TranslateScheduler {
    private const val NAME = "translate_synopses"

    /** Queues one background pass (deduplicated: nothing is added while one is already waiting). */
    fun kick(context: Context, delayMinutes: Long = 0, chain: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<TranslateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAME, if (chain || delayMinutes > 0) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, req,
        )
    }
}
