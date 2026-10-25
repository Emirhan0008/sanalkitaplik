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
 * Sirasiyla Google Books (API arama -> hacim ID koprusu) -> Open Library.
 */
class BookMetadataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    /** fetch() sonucu: "kayit yok" ile "kaynaklara ulasilamadigini" ayristirmak icin. */
    enum class FetchOutcome { FOUND, NOT_FOUND, SOURCES_UNREACHABLE }

    /** Son fetch() sonucu (elle ISBN girme ekrani mesajini sekillendirir). */
    @Volatile
    var lastOutcome: FetchOutcome = FetchOutcome.NOT_FOUND
        private set

    /** fetch sirasinda herhangi bir kaynak saglikli yanit dondurdu mu? */
    @Volatile
    private var anySourceOk = false

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

    /** Son Google hatasi (Ayalar ekraninda gosterilir); basariyla temizlenir. */
    @Volatile
    private var googleLastError: String? = null

    private fun googleAvailable() = System.currentTimeMillis() >= googleDownUntil

    private fun markGoogleDown(reason: String?) {
        googleDownUntil = System.currentTimeMillis() + 10 * 60_000L
        googleLastError = reason?.take(80) ?: "bilinmeyen hata"
    }

    /** Kaynak sagligi ozeti (Ayalar sekmesi). */
    data class SourceHealth(
        val googleDownUntil: Long,
        val googleLastError: String?
    )

    fun health(): SourceHealth = SourceHealth(googleDownUntil, googleLastError)

    suspend fun fetch(isbn: String, withCover: Boolean = true): RemoteMeta? = withContext(Dispatchers.IO) {
        anySourceOk = false
        // 1) Birincil: Google Books (devre disiysa dogrudan Open Library)
        val google = if (googleAvailable()) {
            runCatching { fromGoogleBooks(isbn) }
                .onFailure { markGoogleDown(it.message) }
                .getOrNull()
        } else null
        if (google != null && google.title.isNotBlank()) {
            // Basarili sorgu: devre kesici durumu ve son hata temizlenir.
            googleLastError = null
            googleDownUntil = 0L
        }
        // 2) Yedek: Google bos/eksik donerse Open Library
        val needsMore = google == null || google.title.isBlank()
        val openLibrary = if (needsMore) {
            runCatching { fromOpenLibrary(isbn) }.getOrNull()
        } else null

        val base = google ?: openLibrary
        if (base == null || base.title.isBlank()) {
            lastOutcome = if (anySourceOk) FetchOutcome.NOT_FOUND else FetchOutcome.SOURCES_UNREACHABLE
            return@withContext null
        }
        lastOutcome = FetchOutcome.FOUND
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
            }.onFailure { markGoogleDown(it.message) }

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

    /**
     * Google Books: once resmi arama (volumes.list), o bulamazsa hacim-ID koprusu.
     * Arama backend'i gecici olarak 503 dondugunde (bilinen backendFailed arizasi)
     * kopru devreye girer: ISBN'den hacim ID'si alinir, kunye resmi volumes.get ile cekilir.
     */
    private fun fromGoogleBooks(isbn: String): Raw? {
        var gotResponse = false
        var lastError: Throwable? = null
        for (candidate in isbnVariants(isbn)) {
            try {
                val json = getJson(googleApiUrl("volumes?q=isbn:$candidate"), attempts = 2)
                gotResponse = true
                val items = json.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    parseGoogleVolume(items.getJSONObject(0))?.let { return it }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        try {
            fromGoogleVolumeBridge(isbn)?.let { return it }
            gotResponse = true
        } catch (e: Exception) {
            lastError = e
        }
        // Hicbir Google sorgusu yanitlanamadiysa devre kesiciyi tetikle.
        if (!gotResponse) throw lastError ?: error("Google Books erisilemedi")
        return null
    }

    /**
     * Google Kitaplar web koprusu: `books?vid=ISBN{isbn}` sayfasindan yalnizca
     * hacim ID'si alinir; kunyenin tamami resmi volumes.get API'sinden gelir.
     * ID, sayfanin canonical/og:url baglantisinda tasiniyor.
     */
    private fun fromGoogleVolumeBridge(isbn: String): Raw? {
        val request = Request.Builder()
            .url("https://books.google.com/books?vid=ISBN$isbn")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Kitaplik/1.0")
            .build()
        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("empty body")
        }
        val id = CANONICAL_ID.find(html)?.groupValues?.get(1)
            ?: GENERIC_ID.find(html)?.groupValues?.get(1)
            ?: return null
        val json = getJson(googleApiUrl("volumes/$id"), attempts = 2)
        val raw = parseGoogleVolume(json) ?: return null
        // Yanlis hacim tasmasin: kayit istenen ISBN'i tasimiyorsa reddet.
        if (!volumeHasIsbn(json, isbn)) return null
        return raw
    }

    private fun parseGoogleVolume(volume: JSONObject): Raw? {
        val info = volume.optJSONObject("volumeInfo") ?: return null
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

    /** Google hacim kaydi istenen ISBN'i tasiyor mu? */
    private fun volumeHasIsbn(volume: JSONObject, isbn: String): Boolean {
        val ids = volume.optJSONObject("volumeInfo")?.optJSONArray("industryIdentifiers") ?: return false
        val wanted = isbnVariants(isbn).toSet()
        for (i in 0 until ids.length()) {
            normalizeIsbn(ids.getJSONObject(i).optString("identifier"))?.let { if (it in wanted) return true }
        }
        return false
    }

    /**
     * Open Library zinciri: baski kaydi (/isbn/) -> zengin kayit (api/books?bibkeys)
     -> alan aramasi (search.json?isbn=). Her adimda ISBN-13/ISBN-10 bicimlerinin
     * ikisi de denenir; bazi yeni ISBN'ler yalnizca eski bicimle kayitlidir.
     */
    private fun fromOpenLibrary(isbn: String): Raw? {
        val candidates = isbnVariants(isbn)

        for (c in candidates) {
            val raw = runCatching {
                val json = getJson("https://openlibrary.org/isbn/$c.json")
                val title = json.optString("title")
                if (title.isBlank()) return@runCatching null
                // Open Library, taninmayan ISBN'lerde baska bir kayda yonlendirebiliyor
                // (spam/yanlis kunye). Kaydin ISBN listesi varsa istenen ISBN icinde olmali.
                if (!listsIsbn(json, c)) return@runCatching null
                Raw(
                    title = title,
                    // Open Library baski kaydinda yazar cogu zaman by_statement alaninda
                    authors = json.optString("by_statement").trim(),
                    publisher = json.optJSONArray("publishers").joinArray(),
                    publishedDate = json.optString("publish_date"),
                    pageCount = json.optInt("number_of_pages", 0),
                    description = json.optString("description").let { if (it.startsWith("{")) "" else it },
                    coverUrl = openLibraryCoverUrl(c)
                )
            }.getOrNull()
            if (raw != null) return raw
        }

        // api/books?bibkeys daha zengindir: yazar/yayinevi adlari gelir.
        for (c in candidates) {
            val raw = runCatching {
                val json = getJson(
                    "https://openlibrary.org/api/books?bibkeys=ISBN:$c&format=json&jscmd=data"
                )
                val node = json.optJSONObject("ISBN:$c") ?: return@runCatching null
                val title = node.optString("title")
                if (title.isBlank()) return@runCatching null
                Raw(
                    title = title,
                    authors = node.optJSONArray("authors").joinObjects("name"),
                    publisher = node.optJSONArray("publishers").joinObjects("name"),
                    publishedDate = node.optString("publish_date"),
                    pageCount = node.optInt("number_of_pages", 0),
                    description = node.optString("excerpt"),
                    coverUrl = node.optJSONObject("cover")?.optString("medium")?.ifBlank { null }
                        ?: openLibraryCoverUrl(c)
                )
            }.getOrNull()
            if (raw != null) return raw
        }

        for (c in candidates) {
            val raw = runCatching {
                val json = getJson(
                    "https://openlibrary.org/search.json?isbn=$c" +
                        "&fields=title,author_name,publisher,first_publish_year,number_of_pages_median&limit=3"
                )
                val docs = json.optJSONArray("docs") ?: return@runCatching null
                if (docs.length() == 0) return@runCatching null
                val doc = docs.getJSONObject(0)
                Raw(
                    title = doc.optString("title"),
                    authors = doc.optJSONArray("author_name").joinArray(),
                    publisher = doc.optJSONArray("publisher").joinArray(),
                    publishedDate = doc.optString("first_publish_year"),
                    pageCount = doc.optInt("number_of_pages_median", 0),
                    description = "",
                    coverUrl = openLibraryCoverUrl(c)
                )
            }.getOrNull()
            if (raw != null) return raw
        }
        return null
    }

    private fun openLibraryCoverUrl(isbn: String) = "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"

    /**
     * Open Library baski kaydi istenen ISBN'i listeliyor mu?
     * Alanlar hic yoksa dogrulanamaz -> kabul (eski kayitlar).
     */
    private fun listsIsbn(json: JSONObject, isbn: String): Boolean {
        // Karsilastirma her zaman ISBN-13 uzerinden: aday ISBN-10 olsa bile.
        val target = normalizeIsbn(isbn) ?: isbn
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
            !junk && normalizeIsbn(cleaned) == target
        }
    }

    /** Google Books API URL'i: anahtar (local.properties) ve ulke parametresi ekler. */
    private fun googleApiUrl(path: String): String {
        val key = BuildConfig.GBOOKS_KEY
        val sep = if ("?" in path) "&" else "?"
        val auth = if (key.isBlank()) "" else "${sep}key=$key"
        // country olmadan bazi IP'lerde totalItems=0 donuyor; cihaz ulkesini gonder.
        val country = "&country=" + java.util.Locale.getDefault().country.ifBlank { "TR" }
        return "https://www.googleapis.com/books/v1/$path$auth$country"
    }

    private fun googleBooksUrl(params: String): String = googleApiUrl("volumes?$params")

    private fun getJson(url: String, attempts: Int = 1): JSONObject {
        var lastError: Exception? = null
        for (attempt in 0 until attempts) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", "Kitaplik/1.0").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code} for $url")
                    anySourceOk = true
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

/** Obje dizisinden bir alani ceker: [{"name":"X"}] -> "X, Y". */
private fun org.json.JSONArray?.joinObjects(key: String): String {
    if (this == null || length() == 0) return ""
    return (0 until length())
        .mapNotNull { optJSONObject(it)?.optString(key)?.takeIf { s -> s.isNotBlank() } }
        .joinToString(", ")
}

/**
 * Kitap sayfasinin canonical/og:url baglantisindaki hacim ID'si.
 * Ornek: <link rel="canonical" href="...books/about/X.html?hl=tr&amp;id=s4FY0AEACAAJ">
 * HTML'de `&` -> `&amp;` oldugu icin `amp;` opsiyonel ele alinir.
 */
private val CANONICAL_ID = Regex("""(?:og:url|canonical)[^>]*?[?&](?:amp;)?id=([A-Za-z0-9_-]{6,})""")

/** Yedek: sayfadaki herhangi bir `books?id=...` referansi (vid= gibi sahte eslesmeleri onler). */
private val GENERIC_ID = Regex("""books\?[^"'<>]*?[?&](?:amp;)?id=([A-Za-z0-9_-]{6,})""")
