package com.animeshadow.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.ArrayDeque

/**
 * Keeps us inside a public API's rate limits: a sliding-window limiter (max [maxRequests] per [windowMs]) plus a
 * minimum spacing between calls, and a bounded retry with backoff on HTTP 429 / 5xx that honours Retry-After.
 * Runs on OkHttp's background threads only (never the main thread).
 */
class PoliteInterceptor(
    private val maxRequests: Int,
    private val windowMs: Long,
    private val minSpacingMs: Long,
    private val maxRetries: Int = 3,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    private val sent = ArrayDeque<Long>()
    private val lock = Any()

    /** Blocks until a request may be sent; returns how long it waited (for tests). */
    fun acquire(): Long {
        var waited = 0L
        while (true) {
            val wait: Long
            synchronized(lock) {
                val now = clock()
                while (sent.isNotEmpty() && now - sent.peekFirst()!! >= windowMs) sent.pollFirst()
                val spacing = sent.peekLast()?.let { minSpacingMs - (now - it) } ?: 0L
                val windowWait = if (sent.size >= maxRequests) windowMs - (now - sent.peekFirst()!!) else 0L
                wait = maxOf(spacing, windowWait, 0L)
                if (wait == 0L) {
                    sent.addLast(now)
                    return waited
                }
            }
            sleeper(wait)
            waited += wait
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        while (true) {
            acquire()
            val response = chain.proceed(chain.request())
            val retryable = response.code == 429 || response.code in 500..599
            if (!retryable || attempt >= maxRetries) return response
            val retryAfterSec = response.header("Retry-After")?.trim()?.toLongOrNull()
            response.close()
            val backoff = retryAfterSec?.times(1000) ?: (1000L shl attempt)
            sleeper(backoff.coerceIn(500L, 60_000L))
            attempt++
            if (Thread.currentThread().isInterrupted) throw IOException("interrupted")
        }
    }
}
