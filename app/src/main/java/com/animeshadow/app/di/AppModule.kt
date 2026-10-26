package com.animeshadow.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.AnimeRepository
import com.animeshadow.app.data.AppJson
import com.animeshadow.app.data.BackupManager
import com.animeshadow.app.data.LibraryRepository
import com.animeshadow.app.data.local.AppDatabase
import com.animeshadow.app.data.remote.AniListApi
import com.animeshadow.app.data.remote.JikanApi
import com.animeshadow.app.data.remote.MalPage
import com.animeshadow.app.data.NotificationRepository
import com.animeshadow.app.data.translate.MyMemoryTranslator
import com.animeshadow.app.sync.TranslateScheduler
import com.animeshadow.app.BuildConfig
import com.animeshadow.app.data.translate.SynopsisTranslator
import com.animeshadow.app.data.remote.PoliteInterceptor
import com.animeshadow.app.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object Network {
    const val ANILIST_URL = "https://graphql.anilist.co/"
    const val JIKAN_URL = "https://api.jikan.moe/"

    fun client(limiter: PoliteInterceptor, connectS: Long = 15, readS: Long = 25, callS: Long = 90): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectS, TimeUnit.SECONDS)
        .readTimeout(readS, TimeUnit.SECONDS)
        .callTimeout(callS, TimeUnit.SECONDS)
        .addInterceptor(limiter)
        .addInterceptor { chain ->
            val req = chain.request()
            chain.proceed(if (req.header("User-Agent") != null) req else req.newBuilder().header("User-Agent", "AnimeShadow/1.1 (Android)").build())
        }
        .build()

    fun aniList(baseUrl: String, client: OkHttpClient): AniListApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AniListApi::class.java)

    fun jikan(baseUrl: String, client: OkHttpClient): JikanApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(JikanApi::class.java)

    /** AniList allows 90/min (currently degraded to 30/min): stay under 25/min. */
    fun aniListLimiter() = PoliteInterceptor(maxRequests = 25, windowMs = 60_000, minSpacingMs = 350)

    /** Jikan: 3 requests/second and 60/minute. */
    fun jikanLimiter() = PoliteInterceptor(maxRequests = 55, windowMs = 60_000, minSpacingMs = 400, maxRetries = 1)

    /** MyMemory: polite spacing so a background pass never bursts. */
    fun translateLimiter() = PoliteInterceptor(maxRequests = 30, windowMs = 60_000, minSpacingMs = 500, maxRetries = 1)

    /** The MAL page fallback: one page per details visit at most (12 h cache), well under MAL's limits. */
    fun malPageLimiter() = PoliteInterceptor(maxRequests = 20, windowMs = 60_000, minSpacingMs = 1_000, maxRetries = 1)
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun clock(): Clock = Clock.System

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()

    @Provides @Singleton
    fun aniListApi(): AniListApi = Network.aniList(Network.ANILIST_URL, Network.client(Network.aniListLimiter()))

    @Provides @Singleton
    fun jikanApi(): JikanApi = Network.jikan(Network.JIKAN_URL, Network.client(Network.jikanLimiter(), connectS = 6, readS = 10, callS = 20))

    @Provides @Singleton
    fun malPage(): MalPage = MalPage(Network.client(Network.malPageLimiter(), connectS = 10, readS = 15, callS = 30))

    @Provides @Singleton
    fun animeRepository(@ApplicationContext context: Context, db: AppDatabase, api: AniListApi, jikan: JikanApi, clock: Clock, malPage: MalPage) =
        AnimeRepository(db, api, jikan, clock, malPage).apply {
            // v1.1.1: whenever synopses arrive (home, search, lists, details), translate them in the background.
            onSynopsesStored = { runCatching { TranslateScheduler.kick(context) } }
        }

    @Provides @Singleton
    fun notificationRepository(db: AppDatabase, clock: Clock) = NotificationRepository(db.notificationDao(), clock)

    @Provides @Singleton
    fun synopsisTranslator(): SynopsisTranslator =
        MyMemoryTranslator(Network.client(Network.translateLimiter(), connectS = 10, readS = 20, callS = 40), BuildConfig.MYMEMORY_EMAIL)

    @Provides @Singleton
    fun libraryRepository(db: AppDatabase, clock: Clock) = LibraryRepository(db, clock)

    @Provides @Singleton
    fun backupManager(db: AppDatabase) = BackupManager(db)

    @Provides @Singleton
    fun settingsRepository(@ApplicationContext context: Context) = SettingsRepository(context.dataStore)
}
