package com.ender.kitaplik.data

import com.ender.kitaplik.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class RemoteMeta(
    val title: String,
    val authors: String,
    val publisher: String,
    val publishedDate: String,
    val pageCount: Int,
    val description: String,
    val coverBytes: ByteArray?,
    val source: String = ""
)

/**
 * Cevrimici metadata kaynagi. Sadece kitap eklenirken cagrilir.
 * Sirasiyla Google Books -> Open Library.
 */
class BookMetadataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /** Kapak indirme kisa timeout'lu: yavas CDN kutuphaneyi bekletmesin. */
    private val coverClient: OkHttpClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /**
     * Google Books ust uste hata verirse (429/5xx) 10 dakika devre disi kalir;
     * boylece her taramada bosuna bekleme olmaz, dogrudan Open Library'ye gecilir.
     */
    @Volatile
    private var googleDownUntil = 0L

    private fun googleAvailable() = System.currentTimeMillis() >= googleDownUntil

    private fun markGoogleDown() {
        googleDownUntil = System.currentTimeMillis() + 10 * 60_000L
    }

    suspend fun fetch(isbn: String, withCover: Boolean = true): RemoteMeta? = withContext(Dispatchers.IO) {
        // 1) Birincil: Google Books (devre disiysa dogrudan Open Library)
        val google = if (googleAvailable()) {
            runCatching { fromGoogleBooks(isbn) }
                .onFailure { markGoogleDown() }
                .getOrNull()
        } else null
        // 2) Yedek: Google bos/eksik donerse Open Library
        val needsMore = google == null || google.title.isBlank()
        val openLibrary = if (needsMore) {
            runCatching { fromOpenLibrary(isbn) }.getOrNull()
        } else null

        val base = google ?: openLibrary ?: return@withContext null
        val source = if (google != null && google.title.isNotBlank()) "Google Books" else "Open Library"

        // Kapak: Google thumbnail -> Open Library kapagi (biri 404/bozuksa digerine dus)
        val candidates = if (withCover) {
            listOfNotNull(google?.coverUrl, openLibraryCoverUrl(isbn)).distinct()
        } else emptyList()
        val coverBytes = candidates.firstNotNullOfOrNull { url ->
            runCatching { download(url) }.getOrNull()?.takeIf { it.size > 2_000 }
        }

        RemoteMeta(
            title = base.title,
            authors = base.authors,
            publisher = base.publisher,
            publishedDate = base.publishedDate,
            pageCount = base.pageCount,
            description = base.description,
            coverBytes = coverBytes,
            source = source
        )
    }

    /**
     * Yalnizca kapak indirir (acilista sessiz on indirme icin).
     * Once Open Library'nin dogrudan ISBN kapagi, olmazsa Google thumbnail denenir;
     * tam kunye sorgusu yapilmadigi icin kotayi tuketmez.
     */
    suspend fun fetchCoverBytes(isbn: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { download(openLibraryCoverUrl(isbn)) }.getOrNull()
            ?.takeIf { it.size > 2_000 }
            ?: runCatching {
                val google = if (googleAvailable()) fromGoogleBooks(isbn) else null
                google?.coverUrl?.let { download(it) }
            }.getOrNull()?.takeIf { it.size > 2_000 }
    }

    /**
     * Ayni eserin diger baskilarinin ISBN'leri.
     * Google Books (baslik + yazar) ve Open Library aramasi birlestirilir.
     */
    suspend fun otherEditionIsbns(title: String, authors: String): List<String> =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext emptyList()
            val found = LinkedHashSet<String>()
            val author = authors.substringBefore(',').trim()

            if (googleAvailable()) runCatching {
                val query = buildString {
                    append("intitle:").append(title.trim())
                    if (author.isNotBlank()) append(" inauthor:").append(author)
                }
                val url = googleBooksUrl(
                    "q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&maxResults=20"
                )
                val items = getJson(url, attempts = 2).optJSONArray("items") ?: return@runCatching
                for (i in 0 until items.length()) {
                    val ids = items.getJSONObject(i)
                        .optJSONObject("volumeInfo")
                        ?.optJSONArray("industryIdentifiers")
                        ?: continue
                    for (j in 0 until ids.length()) {
                        normalizeIsbn(ids.getJSONObject(j).optString("identifier"))?.let { found += it }
                    }
                }
            }.onFailure { markGoogleDown() }

            runCatching {
                val query = buildString {
                    append("title:\"").append(title.trim()).append('"')
                    if (author.isNotBlank()) append(" author:\"").append(author).append('"')
                }
                val url = "https://openlibrary.org/search.json?q=" +
                    java.net.URLEncoder.encode(query, "UTF-8") + "&fields=isbn&limit=5"
                val docs = getJson(url).optJSONArray("docs") ?: return@runCatching
                for (i in 0 until docs.length()) {
                    val isbns = docs.getJSONObject(i).optJSONArray("isbn") ?: continue
                    for (j in 0 until isbns.length()) {
                        normalizeIsbn(isbns.optString(j))?.let { found += it }
                    }
                }
            }

            found.take(60).toList()
        }

    private data class Raw(
        val title: String,
        val authors: String,
        val publisher: String,
        val publishedDate: String,
        val pageCount: Int,
        val description: String,
        val coverUrl: String? = null
    )

    private fun fromGoogleBooks(isbn: String): Raw? {
        val json = getJson(googleBooksUrl("q=isbn:$isbn"), attempts = 3)
        val items = json.optJSONArray("items") ?: return null
        if (items.length() == 0) return null
        val info = items.getJSONObject(0).optJSONObject("volumeInfo") ?: return null

        val authors = info.optJSONArray("authors").joinArray()
        val imageLinks = info.optJSONObject("imageLinks")
        val thumb = imageLinks?.optString("thumbnail")
            ?.replace("http://", "https://")
            ?.replace("&zoom=1", "&zoom=2")

        return Raw(
            title = info.optString("title").ifBlank { "Bilinmeyen kitap" },
            authors = authors,
            publisher = info.optString("publisher"),
            publishedDate = info.optString("publishedDate"),
            pageCount = info.optInt("pageCount", 0),
            description = info.optString("description"),
            coverUrl = thumb
        )
    }

    private fun fromOpenLibrary(isbn: String): Raw? {
        val json = getJson("https://openlibrary.org/isbn/$isbn.json")
        val title = json.optString("title")
        if (title.isBlank()) return null
        // Open Library, taninmayan ISBN'lerde baska bir kayda yonlendirebiliyor
        // (spam/yanlis kunye). Kaydin ISBN listesi varsa istenen ISBN icinde olmali.
        if (!listsIsbn(json, isbn)) return null
        return Raw(
            title = title,
            // Open Library baski kaydinda yazar cogu zaman by_statement alaninda
            authors = json.optString("by_statement").trim(),
            publisher = json.optJSONArray("publishers").joinArray(),
            publishedDate = json.optString("publish_date"),
            pageCount = json.optInt("number_of_pages", 0),
            description = json.optString("description").let { if (it.startsWith("{")) "" else it },
            coverUrl = openLibraryCoverUrl(isbn)
        )
    }

    private fun openLibraryCoverUrl(isbn: String) = "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"

    /**
     * Open Library baski kaydi istenen ISBN'i listeliyor mu?
     * Alanlar hic yoksa dogrulanamaz -> kabul (eski kayitlar).
     */
    private fun listsIsbn(json: JSONObject, isbn: String): Boolean {
        val listed = mutableListOf<String>()
        json.optJSONArray("isbn_13")?.let { arr ->
            for (i in 0 until arr.length()) listed += arr.optString(i)
        }
        json.optJSONArray("isbn_10")?.let { arr ->
            for (i in 0 until arr.length()) listed += arr.optString(i)
        }
        if (listed.isEmpty()) return true
        return listed.any { raw ->
            val cleaned = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
            // OL'de "0000000000" gibi sahte ISBN'ler checksum'i gecse bile kaydi guvenilmez kilar.
            val junk = cleaned.count { it == '0' } >= 7
            !junk && normalizeIsbn(cleaned) == isbn
        }
    }

    /** Google Books URL'ine anahtar (local.properties) ve ulke parametresi ekler. */
    private fun googleBooksUrl(params: String): String {
        val key = BuildConfig.GBOOKS_KEY
        val auth = if (key.isBlank()) "" else "&key=$key"
        // country olmadan bazi IP'lerde totalItems=0 donuyor; cihaz ulkesini gonder.
        val country = java.util.Locale.getDefault().country.ifBlank { "TR" }
        return "https://www.googleapis.com/books/v1/volumes?$params$auth&country=$country"
    }

    private fun getJson(url: String, attempts: Int = 1): JSONObject {
        var lastError: Exception? = null
        for (attempt in 0 until attempts) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", "Kitaplik/1.0").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code} for $url")
                    return JSONObject(response.body?.string() ?: error("empty body"))
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < attempts - 1) Thread.sleep(350L * (attempt + 1))
            }
        }
        throw lastError ?: error("request failed")
    }

    private fun download(url: String): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", "Kitaplik/1.0").build()
        coverClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body?.bytes() ?: error("empty body")
        }
    }
}

private fun org.json.JSONArray?.joinArray(): String {
    if (this == null || length() == 0) return ""
    return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
        .joinToString(", ")
}
