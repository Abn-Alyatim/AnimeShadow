package com.animeshadow.app.ui.common

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.animeshadow.app.R

/**
 * v1.3 item 7: Madyan's video (res/raw/splash_video.mp4, 640×360, no sound, 466 KB) played muted and looped on a
 * TextureView (no SurfaceView hole, fades with the rest of the UI). Its first frame (splash_poster) shows until the
 * video renders, so there is never a black flash.
 */
@Composable
fun SplashVideo(modifier: Modifier = Modifier, loop: Boolean = true) {
    var rendering by remember { mutableStateOf(false) }
    Box(modifier.aspectRatio(16f / 9f).testTag("splash_video")) {
        if (LocalAnimatedLoader.current) {
            val player = remember { arrayOfNulls<MediaPlayer>(1) }
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                val mp = runCatching { MediaPlayer.create(ctx, R.raw.splash_video) }.getOrNull() ?: return
                                player[0] = mp
                                mp.setSurface(Surface(st))
                                mp.isLooping = loop
                                mp.setVolume(0f, 0f)
                                mp.setOnInfoListener { _, what, _ ->
                                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) rendering = true
                                    false
                                }
                                runCatching { mp.start() }
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                runCatching { player[0]?.release() }
                                player[0] = null
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(Unit) { onDispose { runCatching { player[0]?.release() }; player[0] = null } }
        }
        Image(
            painterResource(R.drawable.splash_poster), null,
            Modifier.fillMaxSize().alpha(if (rendering) 0f else 1f),
            contentScale = ContentScale.Crop,
        )
    }
}
