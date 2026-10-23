package com.ender.kitaplik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val isbn: String,
    val title: String,
    val authors: String = "",
    val publisher: String = "",
    val publishedDate: String = "",
    val pageCount: Int = 0,
    val description: String = "",
    val coverPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    /** false -> metadata henuz indirilemedi (cevrimdisi eklendi) */
    val enriched: Boolean = false,
    /** Basarisiz kunye denemesi sayisi; 5'ten sonra detayda "Kunye eksik" rozeti cikar. */
    val enrichAttempts: Int = 0
)
