package com.ender.kitaplik.data

import android.content.Context
import com.ender.kitaplik.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryRepository(
    private val dao: BookDao,
    private val metadataSource: BookMetadataSource,
    private val appContext: Context,
    private val scope: CoroutineScope
) {

    private val coversDir: File
        get() = File(appContext.filesDir, "covers").apply { mkdirs() }

    /** Ayni ISBN icin paralel enrich (addIsbn + retryPending) engellenir. */
    private val inFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    val books: Flow<List<BookEntity>> = dao.observeAll()

    suspend fun find(isbn: String): BookEntity? = dao.findByIsbn(isbn)

    /**
     * Cevrimdisi calisir: kayit hemen olusur, metadata arka planda gelir.
     * @return false -> kitap zaten kutuphanede
     */
    suspend fun addIsbn(isbn: String): Boolean {
        val existing = dao.findByIsbn(isbn)
        if (existing != null) return false

        val inserted = dao.insert(
            BookEntity(
                isbn = isbn,
                title = "Kitap bilgileri getiriliyor",
                addedAt = nextOrder()
            )
        )
        if (inserted == -1L) return false

        scope.launch { enrich(isbn) }
        return true
    }

    suspend fun retryPending() {
        scope.launch {
            dao.pendingEnrichment().forEach { enrich(it.isbn) }
        }
    }

    suspend fun enrich(isbn: String) {
        if (!inFlight.add(isbn)) return
        try {
            val current = dao.findByIsbn(isbn) ?: return
            val remote = runCatching { metadataSource.fetch(isbn) }.getOrNull() ?: return
            val coverPath = remote.coverBytes?.let { writeCover(isbn, it) }

            dao.update(
                current.copy(
                    title = remote.title.ifBlank { current.title },
                    authors = remote.authors.ifBlank { current.authors },
                    publisher = remote.publisher.ifBlank { current.publisher },
                    publishedDate = remote.publishedDate.ifBlank { current.publishedDate },
                    pageCount = if (remote.pageCount > 0) remote.pageCount else current.pageCount,
                    description = remote.description.ifBlank { current.description },
                    coverPath = coverPath ?: current.coverPath,
                    enriched = true
                )
            )
        } finally {
            inFlight.remove(isbn)
        }
    }

    private suspend fun writeCover(isbn: String, bytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(coversDir, "$isbn.jpg")
                file.writeBytes(bytes)
                file.absolutePath
            }.getOrNull()
        }

    private suspend fun nextOrder(): Long {
        val newest = System.currentTimeMillis()
        return newest
    }

    /** Demo verisi: yalnizca debug derlemede ve db bosken. */
    suspend fun seedIfEmpty() {
        if (!BuildConfig.DEBUG) return
        if (dao.count() > 0) return

        sampleBooks().forEachIndexed { index, book ->
            dao.insert(book.copy(addedAt = System.currentTimeMillis() - index * 1_000L))
        }
    }

    private fun sampleBooks() = listOf(
        BookEntity("9789750718533", "Tutunamayanlar", "Oğuz Atay", "İletişim", "1971", 724, enriched = true),
        BookEntity("9789753638029", "Kürk Mantolu Madonna", "Sabahattin Ali", "Yapı Kredi", "1943", 220, enriched = true),
        BookEntity("9789750707711", "Saatleri Ayarlama Enstitüsü", "Ahmet Hamdi Tanpınar", "Dergah", "1961", 380, enriched = true),
        BookEntity("9789750726439", "İnce Memed", "Yaşar Kemal", "Yapı Kredi", "1955", 436, enriched = true),
        BookEntity("9789754686203", "Aylak Adam", "Yusuf Atılgan", "Can", "1959", 208, enriched = true),
        BookEntity("9789750508547", "Huzur", "Ahmet Hamdi Tanpınar", "Dergah", "1949", 394, enriched = true),
        BookEntity("9789750738609", "Beyaz Kale", "Orhan Pamuk", "İletişim", "1985", 180, enriched = true),
        BookEntity("9789750739040", "Kırmızı Saçlı Kadın", "Orhan Pamuk", "Yapı Kredi", "2016", 204, enriched = true),
        BookEntity("9789750740244", "Yaban", "Yakup Kadri Karaosmanoğlu", "İletişim", "1932", 214, enriched = true)
    )
}
