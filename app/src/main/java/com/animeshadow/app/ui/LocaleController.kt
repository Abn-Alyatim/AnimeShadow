package com.animeshadow.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** v1.1: the app is Arabic only (the language switch was removed). Pins the per-app locale to Arabic. */
object LocaleController {
    const val TAG = "ar"

    fun apply() {
        val wanted = LocaleListCompat.forLanguageTags(TAG)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }
}
