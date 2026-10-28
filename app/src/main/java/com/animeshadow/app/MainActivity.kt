package com.animeshadow.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.animeshadow.app.data.auth.AniListAuthRepository
import com.animeshadow.app.data.auth.AniListOAuth
import com.animeshadow.app.ui.AppRoot
import com.animeshadow.app.ui.ads.Ads
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AppCompatActivity so the in-app language switch also works on Android 8–12. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var auth: AniListAuthRepository
    private val openAnime = mutableStateOf<Int?>(null)
    private val openDownloads = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // v1.2 bug fix: after a rotation the activity is recreated with the same launch intent, and v1.1 opened the
        // notification's anime a second time. The intent is only acted on for a fresh start.
        if (savedInstanceState == null) handle(intent)
        setContent {
            AnimeShadowTheme {
                AppRoot(
                    openAnimeId = openAnime.value, onOpened = { openAnime.value = null },
                    openDownloads = openDownloads.value, onDownloadsOpened = { openDownloads.value = false },
                )
            }
        }
        // v1.2 ads: the SDK starts on a background thread after the first screen is up.
        Ads.init(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val data = intent.data
        if (data != null && data.scheme == AniListOAuth.REDIRECT_SCHEME) {
            lifecycleScope.launch {
                val ok = auth.handleRedirect(data.toString())
                Toast.makeText(this@MainActivity, if (ok) R.string.anilist_login_done else R.string.anilist_login_failed, Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            openDownloads.value = true
            intent.removeExtra(EXTRA_OPEN_DOWNLOADS)
            return
        }
        openAnime.value = intent.getIntExtra(EXTRA_ANIME_ID, 0).takeIf { it > 0 }
        intent.removeExtra(EXTRA_ANIME_ID)
    }

    companion object {
        const val EXTRA_ANIME_ID = "anime_id"
        const val EXTRA_OPEN_DOWNLOADS = "open_manga_downloads"
    }
}
