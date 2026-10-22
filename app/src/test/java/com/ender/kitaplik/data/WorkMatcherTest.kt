package com.ender.kitaplik.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Farkli baskilar ayni eserdir; ayni basligi tasiyan farkli kitaplar degildir.
 */
class WorkMatcherTest {

    @Test
    fun `ayni kitabin farkli baskilari eslesir`() {
        assertTrue(
            WorkMatcher.sameWork(
                "Kürk Mantolu Madonna", "Sabahattin Ali",
                "Kürk Mantolu Madonna (Özel Baskı)", "Sabahattin Ali"
            )
        )
    }

    @Test
    fun `aksan ve buyuk harf farki eslesmeyi bozmaz`() {
        assertTrue(
            WorkMatcher.sameWork(
                "Tutunamayanlar", "Oğuz Atay",
                "TUTUNAMAYANLAR", "Oguz Atay"
            )
        )
    }

    @Test
    fun `alt baslik eklenmis baski eslesir`() {
        assertTrue(
            WorkMatcher.sameWork(
                "Saatleri Ayarlama Enstitüsü", "Ahmet Hamdi Tanpınar",
                "Saatleri Ayarlama Enstitüsü: Roman", "Ahmet Hamdi Tanpınar"
            )
        )
    }

    @Test
    fun `seri cilt numarasi farkliysa eslesmez`() {
        assertFalse(
            WorkMatcher.sameWork(
                "İnce Memed 1", "Yaşar Kemal",
                "İnce Memed 2", "Yaşar Kemal"
            )
        )
    }

    @Test
    fun `farkli kitaplar eslesmez`() {
        assertFalse(
            WorkMatcher.sameWork(
                "Aylak Adam", "Yusuf Atılgan",
                "Huzur", "Ahmet Hamdi Tanpınar"
            )
        )
    }

    @Test
    fun `ayni baslik farkli yazar eslesmez`() {
        assertFalse(
            WorkMatcher.sameWork(
                "Yaban", "Yakup Kadri Karaosmanoğlu",
                "Yaban", "Başka Bir Yazar"
            )
        )
    }

    @Test
    fun `yazar bos ise baslik yeterli olabilir`() {
        assertTrue(
            WorkMatcher.sameWork(
                "Beyaz Kale", "",
                "Beyaz Kale", "Orhan Pamuk"
            )
        )
    }
}
