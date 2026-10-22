package com.ender.kitaplik.ui.shelf

import com.ender.kitaplik.data.BookEntity
import kotlin.math.abs

/** Raftaki tek bir sirt (dekoratif ya da gercek kitap). */
data class ShelfBook(
    val key: String,
    val title: String,
    val authors: String,
    val widthDp: Float,
    val heightDp: Float,
    val colorSeed: Int,
    val isReal: Boolean,
    val entity: BookEntity? = null
)

data class ShelfRow(
    val books: List<ShelfBook>,
    val spacingDp: Float,
    val heightDp: Float
)

private val TitleA = listOf(
    "Sessiz", "Kayıp", "Bakır", "Solgun", "Kıyıdaki", "Yalnız", "Gizli", "Uzak",
    "Eski", "Kırık", "Sonsuz", "Beyaz", "Kızıl", "Son", "İlk", "Gece",
    "Sabah", "Deniz", "Dağ", "Şehir", "Küçük", "Büyük", "Yorgun", "Parlak",
    "Karanlık", "Aydınlık", "Yabancı", "Tanıdık", "Uzakta", "Yakın"
)

private val TitleB = listOf(
    "Bahçe", "Deniz", "Şehir", "Mektuplar", "Yolculuk", "Oda", "Köprü", "Ada",
    "Hikâye", "Zaman", "Defter", "Kapı", "Fener", "Rüzgâr", "Gölge", "Yaz",
    "Kış", "Şarkı", "Kitap", "Hane", "Sokak", "Liman", "Kuşlar", "Ağaçlar"
)

private val FakeAuthors = listOf(
    "A. Yıldız", "M. Kaya", "S. Demir", "E. Aksoy", "N. Tekin", "B. Sarı",
    "L. Ünal", "H. Çelik", "R. Aydın", "D. Yalçın", "K. Öztürk", "T. Arslan"
)

/**
 * Raftaki toplam sirt sayisi sabittir (varsayilan 180); gercek kitaplar
 * ISBN'lerinden turetilen konumlara yerlestirilir, kalan yerler dekoratif
 * sirtlarla doldurulur. Tum sirtlar TEK EKRANA sigacak sekilde satirlara
 * bolunur ve her satir genisligi tam dolduracak sekilde normalize edilir.
 */
fun buildFittedShelves(
    books: List<BookEntity>,
    innerWidthDp: Float,
    rows: Int,
    totalSlots: Int = 180
): List<ShelfRow> {
    val slotCount = maxOf(totalSlots, books.size)
    val items = arrayOfNulls<ShelfBook>(slotCount)

    books.forEach { book ->
        var index = ((book.isbn.hashCode() * 31 + book.title.hashCode()) and 0x7fffffff) % slotCount
        var guard = 0
        while (items[index] != null && guard < slotCount) {
            index = (index + 1) % slotCount
            guard++
        }
        items[index] = ShelfBook(
            key = book.isbn,
            title = book.title,
            authors = book.authors,
            widthDp = realWidth(book),
            heightDp = realHeight(book),
            colorSeed = (book.isbn.hashCode() and 0x7fffffff),
            isReal = true,
            entity = book
        )
    }

    for (i in 0 until slotCount) {
        if (items[i] == null) items[i] = decorative(i)
    }

    val gap = 0.4f
    val base = slotCount / rows
    val extra = slotCount % rows
    var cursor = 0
    val result = mutableListOf<ShelfRow>()

    for (r in 0 until rows) {
        val count = base + if (r < extra) 1 else 0
        if (count <= 0) continue
        val rowItems = (0 until count).map { items[cursor++]!! }
        val weights = rowItems.map { it.widthDp.toDouble() }
        val weightSum = weights.sum().coerceAtLeast(1.0)
        val available = ((innerWidthDp - gap * (count - 1)) * 0.998f).coerceAtLeast(count * 6f)

        val fitted = rowItems.mapIndexed { i, book ->
            book.copy(widthDp = (available * (weights[i] / weightSum)).toFloat().coerceAtLeast(6f))
        }
        result += ShelfRow(
            books = fitted,
            spacingDp = gap,
            heightDp = fitted.maxOf { it.heightDp }
        )
    }
    return result
}

private fun realWidth(book: BookEntity): Float {
    val pages = book.pageCount.coerceIn(0, 1200)
    val base = 26f + (pages / 90f).coerceIn(0f, 13f)
    return (base + ((book.isbn.hashCode() and 0x7fffffff) % 5)).coerceIn(26f, 46f)
}

private fun realHeight(book: BookEntity): Float =
    150f + ((book.isbn.hashCode() and 0x7fffffff) % 20)

private fun decorative(index: Int): ShelfBook {
    val title = "${TitleA[index % TitleA.size]} ${TitleB[(index / TitleA.size) % TitleB.size]}"
    val seed = index * 2654435761L
    return ShelfBook(
        key = "deco-$index",
        title = title,
        authors = FakeAuthors[(index * 7) % FakeAuthors.size],
        widthDp = 25f + ((seed.hashCode() and 0x7fffffff) % 17).toFloat(),
        heightDp = 146f + (((seed / 3).hashCode() and 0x7fffffff) % 26).toFloat(),
        colorSeed = (seed.hashCode() and 0x7fffffff),
        isReal = false
    )
}

private fun pack(books: List<ShelfBook>, availableWidthDp: Float): List<ShelfRow> {
    val rows = mutableListOf<ShelfRow>()
    var current = mutableListOf<ShelfBook>()
    var used = 0f
    val gap = 2.5f

    fun flush() {
        if (current.isEmpty()) return
        val sum = current.sumOf { it.widthDp.toDouble() }.toFloat()
        val spacing = if (current.size > 1) {
            ((availableWidthDp - sum) / (current.size - 1)).coerceIn(gap, 12f)
        } else 0f
        rows += ShelfRow(
            books = current.toList(),
            spacingDp = spacing,
            heightDp = current.maxOf { it.heightDp }
        )
        current = mutableListOf()
        used = 0f
    }

    books.forEach { book ->
        val needed = if (current.isEmpty()) book.widthDp else book.widthDp + gap
        if (used + needed > availableWidthDp) flush()
        current += book
        used += if (current.size == 1) book.widthDp else book.widthDp + gap
    }
    flush()
    return rows
}
