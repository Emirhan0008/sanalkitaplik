package com.ender.kitaplik.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/**
 * ISBN'den bagimsiz "eser" eslestirme.
 *
 * Ayni kitabin farkli baskilarinda ISBN farklidir; bu yuzden kutuphanede
 * gorunmeyen bir ISBN, kitabin hic olmadigi anlamina gelmez. Baslik ve yazar
 * token'lari uzerinden eser karsilastirmasi yapilir.
 */
object WorkMatcher {

    private val stop = setOf(
        "bir", "ve", "ile", "the", "a", "an", "of", "in", "on", "to", "and",
        "roman", "cilt", "kitap", "kitabi", "baski", "basim", "basimi", "yeni",
        "ozel", "edition", "volume", "vol", "part", "ciltli", "ciltsiz",
        "tam", "metin", "ceviri", "cevirisi"
    )

    private val roman = Regex("^(i|ii|iii|iv|v|vi|vii|viii|ix|x)+$")
    private val trailingVolume = Regex("([0-9]{1,3})\\s*$")

    /** Aksan/lehce farklarini silerek karsilastirilabilir token kumesi uretir. */
    fun tokens(raw: String): Set<String> {
        val flat = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace('\u0131', 'i') // noktasiz i
        return flat.split(Regex("[^a-z0-9]+"))
            .filter { token ->
                token.length >= 2 &&
                    token !in stop &&
                    !token.all { it.isDigit() } &&
                    !roman.matches(token)
            }
            .toSet()
    }

    /**
     * 0..1 arasi benzerlik. Baslik agirlikli, yazar dogrulayici.
     */
    fun score(titleA: String, authorA: String, titleB: String, authorB: String): Float {
        val ta = tokens(titleA)
        val tb = tokens(titleB)
        if (ta.isEmpty() || tb.isEmpty()) return 0f

        val inter = ta.intersect(tb).size
        val union = ta.union(tb).size
        val jaccard = inter.toFloat() / union
        // "Ince Memed" <-> "Ince Memed - Ozel Baski" gibi icerik farklari
        val containment = inter.toFloat() / max(1, minOf(ta.size, tb.size))
        val titleScore = max(jaccard, containment * 0.95f)

        val aa = tokens(authorA)
        val ab = tokens(authorB)
        val authorScore = when {
            aa.isEmpty() || ab.isEmpty() -> 0.55f
            aa.intersect(ab).isNotEmpty() -> 1f
            else -> 0f
        }
        return titleScore * 0.82f + authorScore * 0.18f
    }

    /** Ayni eserin farkli baskisi mi? */
    fun sameWork(titleA: String, authorA: String, titleB: String, authorB: String): Boolean {
        val ta = tokens(titleA)
        val tb = tokens(titleB)
        if (ta.isEmpty() || tb.isEmpty()) return false

        // Seri/cilt numaralari farkliysa ayri kitaptir (Ince Memed 1 vs 2).
        val va = trailingVolume.find(titleA.trim())?.groupValues?.get(1)
        val vb = trailingVolume.find(titleB.trim())?.groupValues?.get(1)
        if (va != null && vb != null && va != vb) return false

        val inter = ta.intersect(tb).size
        val union = ta.union(tb).size
        val jaccard = inter.toFloat() / union
        val containment = inter.toFloat() / max(1, minOf(ta.size, tb.size))

        if (containment < 0.75f && jaccard < 0.6f) return false

        val aa = tokens(authorA)
        val ab = tokens(authorB)
        // Iki tarafta da yazar var ve hic ortak token yok -> farkli eser.
        if (aa.isNotEmpty() && ab.isNotEmpty() && aa.intersect(ab).isEmpty()) return false
        return true
    }
}
