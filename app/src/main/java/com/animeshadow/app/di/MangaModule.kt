package com.animeshadow.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.animeshadow.app.BuildConfig
import com.animeshadow.app.core.Clock
import com.animeshadow.app.data.AppJson
import com.animeshadow.app.data.auth.GoogleAccountRepository
import com.animeshadow.app.data.translate.SynopsisTranslator
import com.animeshadow.app.manga.data.MangaApi
import com.animeshadow.app.manga.data.MangaDatabase
import com.animeshadow.app.manga.data.MangaRepository
import com.animeshadow.app.manga.download.ChapterDownloader
import com.animeshadow.app.manga.download.MangaDownloads
import com.animeshadow.app.manga.download.WorkManagerDownloadRunner
import com.animeshadow.app.manga.reader.ReaderPrefs
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val Context.readerStore: DataStore<Preferences> by preferencesDataStore(name = "manga_reader")

/** v1.3 MANGA mode: its own database file, API client, downloader and reader settings. */
@Module
@InstallIn(SingletonComponent::class)
object MangaModule {
    @Provides @Singleton
    fun mangaDatabase(@ApplicationContext context: Context): MangaDatabase =
        Room.databaseBuilder(context, MangaDatabase::class.java, MangaDatabase.NAME).addMigrations(MangaDatabase.MIGRATION_1_2).build()

    @Provides @Singleton
    fun mangaApi(): MangaApi = Retrofit.Builder()
        .baseUrl(Network.ANILIST_URL)
        .client(Network.client(Network.aniListLimiter()))
        .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MangaApi::class.java)

    @Provides @Singleton
    fun mangaRepository(db: MangaDatabase, api: MangaApi, clock: Clock, translator: SynopsisTranslator, jikan: com.animeshadow.app.data.remote.JikanApi) =
        MangaRepository(db, api, clock, translator, jikan = jikan)

    /** Page images: no polite limiter (that is for AniList), keep-alive pool and enough parallel calls per host. */
    @Provides @Singleton
    fun mangaDownloads(@ApplicationContext context: Context, db: MangaDatabase, clock: Clock): MangaDownloads {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply { maxRequests = 16; maxRequestsPerHost = 8 })
            .build()
        return MangaDownloads(db, File(context.filesDir, "manga"), ChapterDownloader(client), clock, WorkManagerDownloadRunner(context))
    }

    @Provides @Singleton
    fun readerPrefs(@ApplicationContext context: Context) = ReaderPrefs(context.readerStore)

    @Provides @Singleton
    fun googleAccount(@ApplicationContext context: Context) =
        GoogleAccountRepository(context.getSharedPreferences(GoogleAccountRepository.PREFS, Context.MODE_PRIVATE), BuildConfig.GOOGLE_WEB_CLIENT_ID)
}
