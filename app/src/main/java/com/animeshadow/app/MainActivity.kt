package com.animeshadow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.animeshadow.app.ui.AppRoot
import com.animeshadow.app.ui.theme.AnimeShadowTheme
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity so the in-app language switch also works on Android 8–12. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val openAnime = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openAnime.value = intent?.getIntExtra(EXTRA_ANIME_ID, 0)?.takeIf { it > 0 }
        setContent {
            AnimeShadowTheme {
                AppRoot(openAnimeId = openAnime.value, onOpened = { openAnime.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openAnime.value = intent.getIntExtra(EXTRA_ANIME_ID, 0).takeIf { it > 0 }
    }

    companion object {
        const val EXTRA_ANIME_ID = "anime_id"
    }
}
