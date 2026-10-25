package com.ender.kitaplik.data

/**
 * EAN-13 barkodunu / ISBN-10'u ISBN-13'e cevirir; gecersizse null.
 * - 13 hane: check digit dogrulanir
 * - 10 hane: ISBN-10 checksum (X dahil) dogrulanir, sonra 978'e cevrilir
 * - 9790 (ISMN / nota) reddedilir
 */
fun normalizeIsbn(raw: String): String? {
    val cleaned = raw.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
    val isbn13 = when (cleaned.length) {
        13 -> cleaned
        10 -> isbn10ToIsbn13(cleaned) ?: return null
        else -> return null
    }
    if (!isbn13.all { it.isDigit() }) return null
    if (!isbn13.startsWith("978") && !isbn13.startsWith("979")) return null
    if (isbn13.startsWith("9790")) return null
    if (isbn13.take(12) + isbn13CheckDigit(isbn13.take(12)) != isbn13) return null
    return isbn13
}

private fun isbn13CheckDigit(first12: String): Char {
    var sum = 0
    first12.forEachIndexed { index, c ->
        val d = c - '0'
        sum += if (index % 2 == 0) d else d * 3
    }
    val check = (10 - sum % 10) % 10
    return ('0' + check)
}

private fun isbn10ToIsbn13(isbn10: String): String? {
    if (isbn10.length != 10) return null
    var sum = 0
    for (i in 0 until 10) {
        val d = when {
            isbn10[i].isDigit() -> isbn10[i] - '0'
            isbn10[i] == 'X' && i == 9 -> 10
            else -> return null
        }
        sum += d * (10 - i)
    }
    if (sum % 11 != 0) return null
    val body = "978" + isbn10.take(9)
    return body + isbn13CheckDigit(body)
}

/**
 * ISBN-13'ten ISBN-10 uretir (yalnizca 978 on ekli; 979'un ISBN-10'u yoktur).
 * Bazi kayitlarda eski ISBN-10 formu kayitli oldugu icin aramada islevseldir.
 */
fun isbn13ToIsbn10(isbn13: String): String? {
    if (isbn13.length != 13 || !isbn13.startsWith("978")) return null
    val body = isbn13.slice(3..11)
    var sum = 0
    for (i in 0 until 9) sum += (body[i] - '0') * (10 - i)
    val check = (11 - sum % 11) % 11
    return body + (if (check == 10) "X" else check.toString())
}

/**
 * Kunye sorgularinda denenecek ISBN bicimleri: dogal hali + karsilikli donusum.
 * Orn. 9786254085086 -> [9786254085086, 6254085089]
 */
fun isbnVariants(isbn13: String): List<String> =
    listOfNotNull(isbn13, isbn13ToIsbn10(isbn13)).distinct()
