package com.ender.kitaplik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsbnTest {

    @Test
    fun `ean13 gecerli`() {
        assertEquals("9789750718533", normalizeIsbn("9789750718533"))
        assertEquals("9780140328721", normalizeIsbn("978-0-14-032872-1"))
    }

    @Test
    fun `ean13 bozuk check digit reddedilir`() {
        assertNull(normalizeIsbn("9789750718534"))
    }

    @Test
    fun `isbn10 x hanesi ile donusur`() {
        assertEquals("9780975229804", normalizeIsbn("097522980X"))
    }

    @Test
    fun `isbn10 gecersiz checksum reddedilir`() {
        assertNull(normalizeIsbn("1234567890"))
    }

    @Test
    fun `ismn reddedilir`() {
        assertNull(normalizeIsbn("9790060115652"))
    }
}
