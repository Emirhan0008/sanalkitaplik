package com.ender.kitaplik.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ender.kitaplik.KitaplikApp
import com.ender.kitaplik.data.BookEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as KitaplikApp).container.repository

    val books: StateFlow<List<BookEntity>> = repo.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            repo.retryPending()
            repo.prefetchCovers()
        }
    }

    suspend fun find(isbn: String): BookEntity? = repo.find(isbn)

    suspend fun add(isbn: String, prefill: com.ender.kitaplik.data.RemoteMeta? = null): Boolean =
        repo.addIsbn(isbn, prefill)

    suspend fun remove(isbn: String) = repo.remove(isbn)

    suspend fun restore(book: com.ender.kitaplik.data.BookEntity) = repo.restore(book)

    suspend fun lookup(isbn: String): com.ender.kitaplik.data.RemoteMeta? = repo.lookup(isbn)

    /** Ayni eserin farkli baskisi kutuphanede var mi? */
    suspend fun findEdition(isbn: String, meta: com.ender.kitaplik.data.RemoteMeta?): BookEntity? =
        repo.findEditionMatch(isbn, meta)
}
