package com.ender.kitaplik.data

import android.content.Context
import android.net.Uri
import com.ender.kitaplik.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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

    /** Ayarlar sekmesi icin kaynak sagligi ozeti (Google devre kesici + son hata). */
    fun sourceHealth(): BookMetadataSource.SourceHealth = metadataSource.health()

    /** Son lookup() sonucunun turu: "kayit yok" mu, "kaynaklara ulasilamadi" mi? */
    fun fetchOutcome(): BookMetadataSource.FetchOutcome = metadataSource.lastOutcome

    /**
     * Kapak onbellegini siler (dosyalar + coverPath yollari). Donus: silinen dosya sayisi.
     * Yenileri kapak on indirmesi zamanla geri indirir.
     */
    suspend fun clearCoverCache(): Int = withContext(Dispatchers.IO) {
        val files = coversDir.listFiles().orEmpty()
        var deleted = 0
        files.forEach { file -> if (file.isFile && file.delete()) deleted++ }
        dao.clearCoverPaths()
        deleted
    }

    suspend fun find(isbn: String): BookEntity? = dao.findByIsbn(isbn)

    /** Kullanici kitabi kaldirir; kapak dosyasi da silinir. */
    suspend fun remove(isbn: String) {
        dao.delete(isbn)
        withContext(Dispatchers.IO) {
            runCatching { File(coversDir, "$isbn.jpg").delete() }
        }
    }

    /** "Geri al": ayni kayit (ISBN, eklenme sirasi, kapak yolu) aynen geri konur. */
    suspend fun restore(book: BookEntity) {
        dao.insert(book)
    }

    /**
     * Cevrimdisi calisir: kayit hemen olusur, metadata arka planda gelir.
     * @return false -> kitap zaten kutuphanede
     */
    suspend fun addIsbn(isbn: String, prefill: RemoteMeta? = null): Boolean {
        val existing = dao.findByIsbn(isbn)
        if (existing != null) return false

        val coverPath = prefill?.coverBytes?.let { writeCover(isbn, it) }
        val hasMeta = prefill != null && prefill.title.isNotBlank()

        val inserted = dao.insert(
            BookEntity(
                isbn = isbn,
                title = prefill?.title?.ifBlank { "Kitap bilgileri getiriliyor" }
                    ?: "Kitap bilgileri getiriliyor",
                authors = prefill?.authors.orEmpty(),
                publisher = prefill?.publisher.orEmpty(),
                publishedDate = prefill?.publishedDate.orEmpty(),
                pageCount = prefill?.pageCount ?: 0,
                description = prefill?.description.orEmpty(),
                coverPath = coverPath,
                addedAt = nextOrder(),
                enriched = hasMeta
            )
        )
        if (inserted == -1L) return false

        if (!hasMeta) EnrichWorker.enqueue(appContext)
        return true
    }

    /** Tarama aninda kutuphanede yoksa: Google Books -> Open Library sorgusu. */
    suspend fun lookup(isbn: String): RemoteMeta? =
        runCatching { metadataSource.fetch(isbn, withCover = true) }.getOrNull()

    /** Kitap adiyla arama (OCR / elle isimle arama): cevrimici aday listesi. */
    suspend fun searchByTitle(title: String, authors: String): List<SearchCandidate> =
        runCatching { metadataSource.searchByTitle(title, authors) }.getOrDefault(emptyList())

    /**
     * ISBN kutuphanede yok ama ayni eserin baska baskisi olabilir.
     * 1) Yerel kayitlarla baslik/yazar benzerligi
     * 2) Dis kaynaklardan ayni eserin diger ISBN'leri -> yerel ISBN karsilastirmasi
     */
    suspend fun findEditionMatch(isbn: String, meta: RemoteMeta?): BookEntity? {
        if (meta == null || meta.title.isBlank()) return null

        dao.getAll()
            .firstOrNull { book ->
                book.isbn != isbn &&
                    WorkMatcher.sameWork(meta.title, meta.authors, book.title, book.authors)
            }
            ?.let { return it }

        val others = runCatching { metadataSource.otherEditionIsbns(meta.title, meta.authors) }
            .getOrDefault(emptyList())
            .filter { it != isbn }
        if (others.isEmpty()) return null

        return dao.findByIsbns(others).firstOrNull()
    }

    /** WorkManager kuyruguna kunye tamamlama isi birakir (tek kuyruk, tekrar eklenmez). */
    fun enqueueEnrich() = EnrichWorker.enqueue(appContext)

    suspend fun pendingIsbns(): List<String> = dao.pendingEnrichment().map { it.isbn }

    /** Rozetteki "yeniden dene": deneme sayacini sifirlar ve kuyruga birakir. */
    suspend fun retryEnrich(isbn: String) {
        val current = dao.findByIsbn(isbn) ?: return
        dao.update(current.copy(enrichAttempts = 0))
        enqueueEnrich()
    }

    /**
     * Acilista kapaksiz kitaplar icin sessiz on indirme.
     * - Tek seferde en fazla [limit] kitap, ayni anda en fazla 3 istek (IO dostu).
     * - Basarisiz indirme uretilmis kapakta kalir; bir sonraki acilista yeniden denenir.
     * - Kullanici raf/liste kaydirirken kare dusurmemesi icin ayri coroutine'de calisir.
     */
    fun prefetchCovers(limit: Int = 10) {
        scope.launch {
            // 1) Referansi kirilmis kapaklar: dosyasi silinmisse yolu temizle,
            //    boylece asagidaki on indirme bunlari da onarir.
            dao.getAll().forEach { book ->
                val path = book.coverPath ?: return@forEach
                val exists = withContext(Dispatchers.IO) { File(path).exists() }
                if (!exists) dao.update(book.copy(coverPath = null))
            }

            // 2) Kapaksiz kitaplar icin sessiz indirme (eski kitaplar once).
            val missing = dao.withoutCover(limit)
            if (missing.isEmpty()) return@launch
            val gate = Semaphore(3)
            missing.map { book ->
                async {
                    gate.withPermit {
                        if (!inFlight.add(book.isbn)) return@withPermit
                        try {
                            val bytes = metadataSource.fetchCoverBytes(book.isbn) ?: return@withPermit
                            val path = writeCover(book.isbn, bytes) ?: return@withPermit
                            val current = dao.findByIsbn(book.isbn) ?: return@withPermit
                            if (current.coverPath == null) dao.update(current.copy(coverPath = path))
                        } catch (_: Exception) {
                            // sessiz: on indirme basarisizligi kullaniciya yansimaz
                        } finally {
                            inFlight.remove(book.isbn)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Kunyeyi indirir. Donus: true -> tamamlandi (ya da kitap artik yok),
     * false -> bu denemede basarisiz (deneme sayaci artar, WorkManager yeniden dener).
     */
    suspend fun enrich(isbn: String): Boolean {
        if (!inFlight.add(isbn)) return true
        try {
            val current = dao.findByIsbn(isbn) ?: return true
            val remote = runCatching { metadataSource.fetch(isbn) }.getOrNull()
            if (remote == null || remote.title.isBlank()) {
                dao.update(current.copy(enrichAttempts = current.enrichAttempts + 1))
                return false
            }
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
                    enriched = true,
                    enrichAttempts = 0
                )
            )
            return true
        } finally {
            inFlight.remove(isbn)
        }
    }

    // ------------------------------------------------------------ yedekleme

    /**
     * Kutuphaneyi JSON olarak verilen dosyaya yazar.
     * @return yazilan kitap sayisi
     */
    suspend fun exportBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val books = dao.getAll().sortedBy { it.addedAt }
        val json = LibraryBackup.toJson(books)
        val stream = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Dosya açılamadı")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        books.size
    }

    /**
     * JSON yedegini ice aktarir. Var olan ISBN'ler atlanir; kapaklar ardindan
     * normal on indirme akisiyla geri gelir.
     */
    suspend fun importBackup(uri: Uri): LibraryBackup.ImportReport = withContext(Dispatchers.IO) {
        val text = appContext.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Dosya açılamadı")

        val parsed = LibraryBackup.parse(text)
        var added = 0
        var existing = 0
        parsed.books.forEach { book ->
            if (dao.findByIsbn(book.isbn) != null) {
                existing++
            } else if (dao.insert(book) != -1L) {
                added++
            } else {
                existing++
            }
        }

        if (added > 0) {
            // Kunyesi eksik gelenler icin kuyruk; kapaklar on indirme ile tamamlanir.
            EnrichWorker.enqueue(appContext)
            prefetchCovers()
        }
        LibraryBackup.ImportReport(
            added = added,
            skippedExisting = existing,
            skippedInvalid = parsed.skippedInvalid,
            totalInFile = parsed.books.size + parsed.skippedInvalid
        )
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
