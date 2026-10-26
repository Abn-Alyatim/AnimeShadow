package com.animeshadow.app.testutil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.animeshadow.app.data.local.AppDatabase
import java.io.File

object TestDb {
    fun inMemory(): AppDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    /** A real on-disk SQLite file, so tests can close and reopen it (persistence across app restarts). */
    fun onDisk(file: File): AppDatabase = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java, file.absolutePath)
        .allowMainThreadQueries()
        .build()
}
