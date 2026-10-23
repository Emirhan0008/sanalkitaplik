package com.ender.kitaplik.ui.shelf

import com.ender.kitaplik.data.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfSearchTest {

    private fun entity(isbn: String, title: String, authors: String = "") =
        BookEntity(isbn = isbn, title = title, authors = authors)

    private fun shelf(title: String, authors: String = "") = ShelfBook(
        key = "k",
        title = title,
        authors = authors,
        widthDp = 30f,
        heightDp = 160f,
        colorSeed = 1,
        isReal = true
    )

    @Test
    fun `Turkce buyuk harf ve aksan duyarsiz`() {
        val book = shelf("Kürk Mantolu Madonna", "Sabahattin Ali")
        assertTrue(matchesQuery(book, "KÜRK"))
        assertTrue(matchesQuery(book, "kurk"))
        assertTrue(matchesQuery(book, "Kürk"))
    }

    @Test
    fun `i noktali ve noktasiz harfler eslesir`() {
        assertTrue(matchesQuery(shelf("İnce Memed"), "ince"))
        assertTrue(matchesQuery(shelf("Işık Ülkesi"), "isik"))
        assertTrue(matchesQuery(shelf("Şeker Portakalı"), "seker"))
    }

    @Test
    fun `yazar adiyla da bulunur`() {
        val book = shelf("Tutunamayanlar", "Oğuz Atay")
        assertTrue(matchesQuery(book, "oguz"))
        assertTrue(matchesQuery(book, "atay"))
        assertFalse(matchesQuery(book, "yaşar kemal"))
    }

    @Test
    fun `birden fazla kelime ayni anda aranir`() {
        val book = shelf("Kürk Mantolu Madonna", "Sabahattin Ali")
        assertTrue(matchesQuery(book, "kurk sabahattin"))
        assertFalse(matchesQuery(book, "kurk orwell"))
    }

    @Test
    fun `bos sorgu eslesmez`() {
        assertFalse(matchesQuery(shelf("Herhangi"), "   "))
    }

    @Test
    fun `siralama baslik eslesmesini one alir`() {
        val books = listOf(
            entity("1", "Karanlık Liman", "Sabahattin Ali"),
            entity("2", "Kürk Mantolu Madonna", "Sabahattin Ali"),
            entity("3", "Başka Kitap", "Kürk Yazar")
        )
        val ranked = rankMatches(books, "kurk")
        assertEquals(listOf("2", "3"), ranked.map { it.key })
    }
}
