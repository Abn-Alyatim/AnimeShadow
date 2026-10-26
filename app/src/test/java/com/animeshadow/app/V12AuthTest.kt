package com.animeshadow.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.auth.AniListAuthRepository
import com.animeshadow.app.data.auth.AniListOAuth
import com.animeshadow.app.data.remote.PoliteInterceptor
import com.animeshadow.app.di.Network
import com.animeshadow.app.testutil.FakeClock
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** v1.2 item 4: AniList login (implicit grant) behind a client id. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V12AuthTest {
    private lateinit var server: MockWebServer
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock()

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun repo(clientId: String): AniListAuthRepository {
        val prefs = ctx.getSharedPreferences("t_" + clientId.ifBlank { "none" }, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val api = Network.aniList(server.url("/").toString(), Network.client(PoliteInterceptor(1000, 60_000, 0, maxRetries = 0)))
        return AniListAuthRepository(prefs, api, clock, clientId)
    }

    @Test fun build_has_no_client_id_so_login_is_not_activated() {
        assertEquals("", BuildConfig.ANILIST_CLIENT_ID)
        val r = repo("")
        assertFalse(r.configured)
        assertNull(r.authorizeUrl())
        assertFalse(r.account.value.configured)
    }

    @Test fun authorize_url_is_the_implicit_grant() {
        assertEquals("https://anilist.co/api/v2/oauth/authorize?client_id=12345&response_type=token", repo("12345").authorizeUrl())
    }

    @Test fun parses_the_token_from_the_redirect_fragment() {
        val t = AniListOAuth.parseRedirect("animeshadow://anilist-auth#access_token=abc.def&token_type=Bearer&expires_in=31536000", 1_000L)!!
        assertEquals("abc.def", t.accessToken)
        assertEquals(1_000L + 31_536_000_000L, t.expiresAt)
        assertNull(AniListOAuth.parseRedirect("animeshadow://anilist-auth#error=access_denied", 0))
        assertNull(AniListOAuth.parseRedirect("https://evil.example/#access_token=x", 0))
        assertNull(AniListOAuth.parseRedirect("animeshadow://anilist-auth", 0))
    }

    @Test fun redirect_logs_in_and_reads_the_viewer() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setBody("""{"data":{"Viewer":{"id":42,"name":"Madyan","avatar":{"large":"https://s4.anilist.co/a.png"}}}}"""))
        val r = repo("12345")
        assertTrue(r.handleRedirect("animeshadow://anilist-auth#access_token=TOKEN1&token_type=Bearer&expires_in=3600"))
        val req = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("Bearer TOKEN1", req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains("Viewer"))
        val a = r.account.value
        assertTrue(a.loggedIn)
        assertEquals("Madyan", a.name)
        assertEquals("TOKEN1", r.token())
        // Expired tokens are not used.
        clock.advance(3_601_000L)
        assertNull(r.token())
        r.logout()
        assertFalse(r.account.value.loggedIn)
    }
}
