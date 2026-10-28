package com.animeshadow.app.ui.common

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.animeshadow.app.ui.theme.Palette

/**
 * Width / height of res/raw/splash_video.mp4, splash_poster and the Shadow loop (1056×720 / 528×360).
 * v1.3.1: the clip was re-made so it has NO box: its dark-grey haze is crushed to pure #000 and all four edges are
 * feathered into #000 (the flames used to be cut by the frame's top and bottom). Every edge pixel is black, so on the
 * black screen only the character and the flames show, at any size; the clip is also cropped to the character
 * (the black sides are gone), so filling the width shows him ~1.2× larger than 1.3.0.
 */
const val SHADOW_CLIP_ASPECT = 1056f / 720f

/**
 * v1.3 item 7: Madyan's video (res/raw/splash_video.mp4, 1056×720 H.264, no sound, a seamless 3.7 s loop played
 * twice = 7.3 s) played muted on a TextureView (no SurfaceView hole, fades with the rest of the UI). Its first frame
 * (splash_poster) shows until the video renders — and again whenever the surface is re-created (app back from the
 * background), so there is never a black or stale box.
 */
@Composable
fun SplashVideo(modifier: Modifier = Modifier, loop: Boolean = true) {
    var rendering by remember { mutableStateOf(false) }
    Box(modifier.aspectRatio(SHADOW_CLIP_ASPECT).background(Palette.Black).testTag("splash_video")) {
        if (LocalAnimatedLoader.current) {
            val player = remember { arrayOfNulls<MediaPlayer>(1) }
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = false
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
                                if (runCatching { mp.start() }.isFailure) { runCatching { mp.release() }; player[0] = null }
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                // The poster covers again until the next player renders (1.3.0 left it hidden → black box).
                                rendering = false
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
