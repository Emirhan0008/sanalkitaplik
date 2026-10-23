package com.ender.kitaplik

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
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

class KitaplikApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * Kapak onbellegi: 100 MB sabit disk + %15 bellek.
     * Kapaklar yerel dosyadan yukleniyor; disk onbellegi ileride uzak gorseller ve
     * Coil'in tekrar cozumlemesini onlemek icin hazir.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .crossfade(false)
        .build()
}
