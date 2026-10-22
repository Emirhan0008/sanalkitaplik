package com.ender.kitaplik.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isbn = :isbn LIMIT 1")
    suspend fun findByIsbn(isbn: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAll(): List<BookEntity>

    @Query("SELECT * FROM books WHERE isbn IN (:isbns)")
    suspend fun findByIsbns(isbns: List<String>): List<BookEntity>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT * FROM books WHERE enriched = 0")
    suspend fun pendingEnrichment(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE isbn = :isbn")
    suspend fun delete(isbn: String)
}
