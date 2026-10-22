package com.ender.kitaplik.data

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
    val coverBytes: ByteArray?
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

    suspend fun fetch(isbn: String): RemoteMeta? = withContext(Dispatchers.IO) {
        val google = runCatching { fromGoogleBooks(isbn) }.getOrNull()
        val needsMore = google == null || google.title.isBlank()
        val openLibrary = if (needsMore) {
            runCatching { fromOpenLibrary(isbn) }.getOrNull()
        } else null

        val base = google ?: openLibrary ?: return@withContext null

        // Kapak: Google thumbnail -> Open Library kapagi (biri 404/bozuksa digerine dus)
        val candidates = listOfNotNull(google?.coverUrl, openLibraryCoverUrl(isbn)).distinct()
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
            coverBytes = coverBytes
        )
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
        val json = getJson("https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn&country=US")
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
        return Raw(
            title = title,
            authors = "",
            publisher = json.optJSONArray("publishers").joinArray(),
            publishedDate = json.optString("publish_date"),
            pageCount = json.optInt("number_of_pages", 0),
            description = json.optString("description").let { if (it.startsWith("{")) "" else it },
            coverUrl = openLibraryCoverUrl(isbn)
        )
    }

    private fun openLibraryCoverUrl(isbn: String) = "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"

    private fun getJson(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "Kitaplik/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return JSONObject(response.body?.string() ?: error("empty body"))
        }
    }

    private fun download(url: String): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", "Kitaplik/1.0").build()
        client.newCall(request).execute().use { response ->
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
