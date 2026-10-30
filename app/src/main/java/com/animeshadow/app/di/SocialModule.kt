package com.animeshadow.app.di

import android.content.Context
import com.animeshadow.app.BuildConfig
import com.animeshadow.app.data.auth.GoogleAccountRepository
import com.animeshadow.app.social.AppStatusRepository
import com.animeshadow.app.social.SocialApi
import com.animeshadow.app.social.SocialRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** v1.5.0: AnimeShadow backend on Madyan's panel server (same base URL as «شادو سينباي»). */
@Module
@InstallIn(SingletonComponent::class)
object SocialModule {
    @Provides @Singleton
    fun socialApi(account: GoogleAccountRepository): SocialApi = SocialApi(BuildConfig.CHAT_BASE_URL, { account.session })

    @Provides @Singleton
    fun appStatus(@ApplicationContext context: Context, api: SocialApi) =
        AppStatusRepository(api, context.getSharedPreferences(AppStatusRepository.PREFS, Context.MODE_PRIVATE))

    @Provides @Singleton
    fun social(api: SocialApi, account: GoogleAccountRepository) = SocialRepository(api, account)

    @Provides @Singleton
    fun notInterested(@ApplicationContext context: Context) =
        com.animeshadow.app.social.NotInterestedStore(context.getSharedPreferences(com.animeshadow.app.social.NotInterestedStore.PREFS, Context.MODE_PRIVATE))

    @Provides @Singleton
    fun listShare(db: com.animeshadow.app.data.local.AppDatabase, mangaDb: com.animeshadow.app.manga.data.MangaDatabase, ni: com.animeshadow.app.social.NotInterestedStore) =
        com.animeshadow.app.social.ListShare(db, mangaDb, ni)
}
