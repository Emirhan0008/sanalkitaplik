package com.ender.kitaplik.ui.shelf

import com.ender.kitaplik.data.BookEntity

/**
 * Raf aramasi: Turkce buyuk/kucuk harf ve aksan duyarsiz.
 * "KURK", "kürk", "kurk" ayni sonucu verir.
 */
private val FoldMap = mapOf(
    'ı' to 'i', 'İ' to 'i', 'I' to 'i',
    'ş' to 's', 'Ş' to 's',
    'ğ' to 'g', 'Ğ' to 'g',
    'ü' to 'u', 'Ü' to 'u',
    'ö' to 'o', 'Ö' to 'o',
    'ç' to 'c', 'Ç' to 'c',
    'â' to 'a', 'Â' to 'a',
    'î' to 'i', 'Î' to 'i',
    'û' to 'u', 'Û' to 'u'
)

fun foldTr(text: String): String = buildString(text.length) {
    text.forEach { ch ->
        val folded = FoldMap[ch]
        if (folded != null) append(folded) else append(ch.lowercaseChar())
    }
}

/** Sorgunun tum kelimeleri baslik+yazar icinde geciyorsa eslesir. */
fun matchesQuery(book: ShelfBook, query: String): Boolean {
    val needle = foldTr(query).trim()
    if (needle.isEmpty()) return false
    val haystack = foldTr("${book.title} ${book.authors}")
    return needle.split(' ').filter { it.isNotBlank() }.all { haystack.contains(it) }
}

/**
 * Siralama: once basligi sorguyla baslayanlar, sonra baslikta gecenler,
 * en son yalnizca yazarda eslesenler. Boylece "kurk" yazinca
 * *Kürk Mantolu Madonna* ilk sirada gelir.
 */
fun rankMatches(books: List<BookEntity>, query: String): List<ShelfBook> {
    val needle = foldTr(query).trim()
    if (needle.isEmpty()) return emptyList()

    return books
        .mapNotNull { entity ->
            val candidate = ShelfBook(
                key = entity.isbn,
                title = entity.title,
                authors = entity.authors,
                widthDp = 0f,
                heightDp = 0f,
                colorSeed = 0,
                isReal = true,
                entity = entity
            )
            if (!matchesQuery(candidate, query)) return@mapNotNull null
            val title = foldTr(entity.title)
            val score = when {
                title.startsWith(needle) -> 0
                title.contains(needle) -> 1
                else -> 2
            }
            score to candidate
        }
        .sortedWith(compareBy({ it.first }, { it.second.title.length }))
        .map { it.second }
}
