package com.ender.kitaplik

import android.app.Application
import android.content.Context
import com.ender.kitaplik.data.AppDatabase
import com.ender.kitaplik.data.BookMetadataSource
import com.ender.kitaplik.data.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = LibraryRepository(
        dao = AppDatabase.get(context).bookDao(),
        metadataSource = BookMetadataSource(),
        appContext = context.applicationContext,
        scope = appScope
    )
}

class KitaplikApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
