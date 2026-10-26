package com.animeshadow.app.testutil

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import java.security.MessageDigest

/**
 * Serves the real AniList posters/photos (downloaded once into test resources, keyed by sha1(url)) synchronously,
 * so Roborazzi screenshots show real images without network.
 */
object TestImages {
    private fun key(url: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return d.joinToString("") { "%02x".format(it) }.take(16) + url.substringAfterLast('.', "").let { if (it.length in 3..4) ".$it" else "" }
    }

    fun install(context: Context) {
        val loader = ImageLoader.Builder(context)
            .components { add(LocalInterceptor(context)) }
            .dispatcher(Dispatchers.Main.immediate)
            .crossfade(false)
            .memoryCache(null)
            .diskCache(null)
            .build()
        Coil.setImageLoader(loader)
    }

    private class LocalInterceptor(private val context: Context) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val url = chain.request.data as? String ?: return chain.proceed(chain.request)
            val stream = javaClass.classLoader!!.getResourceAsStream("images/" + key(url))
                ?: return ErrorResult(null, chain.request, IllegalStateException("no test image for $url"))
            val bmp = stream.use { BitmapFactory.decodeStream(it) }
                ?: return ErrorResult(null, chain.request, IllegalStateException("undecodable $url"))
            return SuccessResult(BitmapDrawable(context.resources, bmp), chain.request, DataSource.MEMORY)
        }
    }
}
