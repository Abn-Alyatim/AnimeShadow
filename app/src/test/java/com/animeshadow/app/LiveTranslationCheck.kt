package com.animeshadow.app

import com.animeshadow.app.data.translate.MyMemoryTranslator
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Talks to the real MyMemory service, so it only runs when ANIMESHADOW_LIVE_TRANSLATE=1 (never in normal CI runs).
 * Writes what came back to build/live_translation.txt.
 */
class LiveTranslationCheck {
    @Test fun real_service_translates_and_keeps_names() = runBlocking {
        assumeTrue(System.getenv("ANIMESHADOW_LIVE_TRANSLATE") == "1")
        val en = "Asta and Yuno were abandoned at the same church on the same day. Raised together as children, they came to know of the Wizard King."
        val ar = MyMemoryTranslator(OkHttpClient()).translate(en, listOf("title:Black Clover", "Asta", "Yuno Grinberryall"))
        File("build/live_translation.txt").writeText("EN: $en\nAR: $ar\n")
        assertTrue(ar != null && ar.contains("Asta") && ar.any { it in '\u0600'..'\u06FF' })
    }
}
