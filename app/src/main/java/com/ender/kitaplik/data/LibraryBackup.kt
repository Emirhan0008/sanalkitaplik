package com.ender.kitaplik.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kutuphanenin JSON yedegi.
 *
 * Amac: telefon degisince ya da veri temizlenince kutuphaneyi geri getirmek;
 * ayrica harici bir araca (chatbot, tablo) toplu aktarim icin ortak format sunmak.
 *
 * Kapak dosyalari tasinmaz (cihaza ozel yollar). Ice aktarmadan sonra kapaklar
 * normal on indirme akisiyla yeniden iner.
 *
 * Şema:
 * {
 *   "schemaVersion": 1,
 *   "exportedAt": 1729700000000,
 *   "app": "Ender Kütüphane",
 *   "books": [
 *     { "isbn": "9789754686203", "title": "Aylak Adam", "authors": "Yusuf Atılgan",
 *       "publisher": "Can", "publishedDate": "1959", "pageCount": 208,
 *       "description": "", "addedAt": 1729600000000, "enriched": true }
 *   ]
 * }
 */
object LibraryBackup {

    const val SCHEMA_VERSION = 1
    private const val APP_NAME = "Ender Kütüphane"

    /** Ice aktarma sonucu: kullaniciya tek satirlik rapor olarak gosterilir. */
    data class ImportReport(
        val added: Int = 0,
        val skippedExisting: Int = 0,
        val skippedInvalid: Int = 0,
        val totalInFile: Int = 0
    ) {
        val message: String
            get() = when {
                added == 0 && skippedExisting > 0 && skippedInvalid == 0 ->
                    "Yedek zaten kütüphanede ($skippedExisting kitap)"
                added == 0 && skippedInvalid > 0 ->
                    "Dosyada eklenecek kitap yok ($skippedInvalid kayıt geçersiz)"
                else -> buildString {
                    append("$added eklendi")
                    if (skippedExisting > 0) append(", $skippedExisting atlandı (zaten var)")
                    if (skippedInvalid > 0) append(", $skippedInvalid geçersiz kayıt")
                }
            }
    }

    /** Bozuk/uyumsuz dosyada firlatilir; cagiran taraf mesaji kullaniciya gosterir. */
    class BackupException(message: String) : Exception(message)

    // ---------------------------------------------------------------- disa aktarma

    fun toJson(books: List<BookEntity>, exportedAt: Long = System.currentTimeMillis()): String {
        val array = JSONArray()
        books.forEach { book ->
            array.put(
                JSONObject().apply {
                    put("isbn", book.isbn)
                    put("title", book.title)
                    put("authors", book.authors)
                    put("publisher", book.publisher)
                    put("publishedDate", book.publishedDate)
                    put("pageCount", book.pageCount)
                    put("description", book.description)
                    put("addedAt", book.addedAt)
                    put("enriched", book.enriched)
                }
            )
        }
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedAt", exportedAt)
            put("app", APP_NAME)
            put("books", array)
        }.toString(2)
    }

    // ---------------------------------------------------------------- ice aktarma

    /** Cozumlenmis dosya: gecerli kitaplar + atlanan bozuk kayit sayisi. */
    data class Parsed(val books: List<BookEntity>, val skippedInvalid: Int)

    /**
     * Dosyayi cozumler. Kurallar:
     * - `schemaVersion` yoksa/ileri surumse hata (sessizce yanlis okumaktansa acikca reddet).
     * - ISBN'i gecersiz kayitlar atlanir; digerleri dosyadaki sirayla korunur.
     * - Ayni ISBN dosya icinde birden fazla gecerse ilki tutulur.
     */
    fun parse(text: String): Parsed {
        if (text.isBlank()) throw BackupException("Dosya boş")

        val root = runCatching { JSONObject(text) }
            .getOrElse { throw BackupException("Geçerli bir JSON değil") }

        val version = root.optInt("schemaVersion", -1)
        if (version <= 0) throw BackupException("Yedek sürümü okunamadı")
        if (version > SCHEMA_VERSION) {
            throw BackupException("Yedek daha yeni bir sürümden ($version); uygulamayı güncelle")
        }

        val books = root.optJSONArray("books")
            ?: throw BackupException("Yedekte kitap listesi yok")

        val seen = HashSet<String>()
        val result = ArrayList<BookEntity>(books.length())
        var invalid = 0
        for (i in 0 until books.length()) {
            val item = books.optJSONObject(i)
            if (item == null) {
                invalid++
                continue
            }
            val rawIsbn = item.optString("isbn")
            val isbn = normalizeIsbn(rawIsbn)
            if (isbn == null || !seen.add(isbn)) {
                invalid++
                continue
            }
            result += BookEntity(
                isbn = isbn,
                title = item.optString("title").ifBlank { "Kitap bilgileri getiriliyor" },
                authors = item.optString("authors"),
                publisher = item.optString("publisher"),
                publishedDate = item.optString("publishedDate"),
                pageCount = item.optInt("pageCount", 0),
                description = item.optString("description"),
                // Kapak yolu tasinmaz: cihaza ozel dosya sistemi yolu.
                coverPath = null,
                addedAt = item.optLong("addedAt", 0L).takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                enriched = item.optBoolean("enriched", false),
                enrichAttempts = 0
            )
        }
        return Parsed(result, invalid)
    }
}
