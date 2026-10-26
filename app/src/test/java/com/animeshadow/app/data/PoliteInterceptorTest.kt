package com.animeshadow.app.data

import com.animeshadow.app.data.remote.PoliteInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliteInterceptorTest {
    private var now = 0L
    private val slept = mutableListOf<Long>()
    private fun limiter(max: Int, window: Long, spacing: Long, retries: Int = 3) =
        PoliteInterceptor(max, window, spacing, retries, clock = { now }, sleeper = { slept += it; now += it })

    @Test fun enforces_minimum_spacing() {
        val l = limiter(100, 60_000, 400)
        assertEquals(0L, l.acquire())
        assertEquals(400L, l.acquire())
        now += 1_000
        assertEquals(0L, l.acquire())
    }

    @Test fun enforces_window_limit() {
        val l = limiter(3, 1_000, 0) // Jikan-style 3 per second
        repeat(3) { assertEquals(0L, l.acquire()) }
        assertEquals(1_000L, l.acquire())
    }

    @Test fun retries_429_honouring_retry_after() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "2"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(limiter(100, 60_000, 0)).build()
        val r = client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        assertEquals(200, r.code)
        assertEquals("ok", r.body!!.string())
        assertEquals(2, server.requestCount)
        assertTrue(slept.contains(2_000L))
        server.shutdown()
    }

    @Test fun gives_up_after_max_retries_with_exponential_backoff() {
        val server = MockWebServer()
        repeat(4) { server.enqueue(MockResponse().setResponseCode(503)) }
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(limiter(100, 60_000, 0, retries = 2)).build()
        val r = client.newCall(Request.Builder().url(server.url("/")).build()).execute()
        assertEquals(503, r.code)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1_000L, 2_000L), slept.filter { it >= 1_000 })
        server.shutdown()
    }

    @Test fun does_not_retry_client_errors() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(404))
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(limiter(100, 60_000, 0)).build()
        assertEquals(404, client.newCall(Request.Builder().url(server.url("/")).build()).execute().code)
        assertEquals(1, server.requestCount)
        server.shutdown()
    }
}
