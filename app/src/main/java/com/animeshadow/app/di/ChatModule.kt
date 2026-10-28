package com.animeshadow.app.di

import android.content.Context
import com.animeshadow.app.BuildConfig
import com.animeshadow.app.chat.ChatRepository
import com.animeshadow.app.chat.ChatStore
import com.animeshadow.app.chat.HttpChatBackend
import com.animeshadow.app.chat.LibrarySnapshot
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.settings.ExtraPrefs
import com.animeshadow.app.data.settings.extraStore
import com.animeshadow.app.manga.data.MangaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** v1.4.0: «محتوى +18» preference and the «شادو سينباي» chat (history on the phone, AI behind Madyan's panel server). */
@Module
@InstallIn(SingletonComponent::class)
object ChatModule {
    @Provides @Singleton
    fun extraPrefs(@ApplicationContext context: Context) = ExtraPrefs(context.extraStore)

    @Provides @Singleton
    fun chatRepository(@ApplicationContext context: Context, prefs: ExtraPrefs, db: AppDatabase, mangaDb: MangaDatabase): ChatRepository {
        val snapshot = LibrarySnapshot(db, mangaDb)
        return ChatRepository(ChatStore(File(context.filesDir, "chat")), HttpChatBackend(BuildConfig.CHAT_BASE_URL), prefs) { snapshot.build() }
    }
}
