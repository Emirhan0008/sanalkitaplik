package com.ender.kitaplik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ender.kitaplik.data.BookEntity
import com.ender.kitaplik.data.normalizeIsbn
import com.ender.kitaplik.ui.shelf.Bookshelf
import com.ender.kitaplik.ui.shelf.ShelfBook
import com.ender.kitaplik.ui.shelf.buildFittedShelves
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Phase { Idle, Flying, Detail, Closing }

private fun Rect.snapped(): Rect = Rect(
    left = left.roundToInt().toFloat(),
    top = top.roundToInt().toFloat(),
    right = right.roundToInt().toFloat(),
    bottom = bottom.roundToInt().toFloat()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoot() {
    val vm: LibraryViewModel = viewModel()
    val books by vm.books.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(0) }
    var scannerOpen by rememberSaveable { mutableStateOf(false) }
    var unknownIsbn by remember { mutableStateOf<String?>(null) }

    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var flightBook by remember { mutableStateOf<ShelfBook?>(null) }
    var flyStart by remember { mutableStateOf<Rect?>(null) }
    var flyTarget by remember { mutableStateOf<Rect?>(null) }
    var detailIsbn by rememberSaveable { mutableStateOf<String?>(null) }
    var detailVisible by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    val progress = remember { Animatable(0f) }

    val target = targetCoverRect()

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val innerWidthDp = (screenWidthDp - 44).toFloat()
    val rowCount = ((maxOf(180, books.size)) / 20f).roundToInt().coerceIn(5, 10)
    val shelfRows = remember(books, innerWidthDp, rowCount) {
        buildFittedShelves(books, innerWidthDp, rowCount)
    }
    val shelfByIsbn = remember(shelfRows) {
        buildMap { shelfRows.forEach { row -> row.books.forEach { if (it.isReal) put(it.key, it) } } }
    }
    val detailBook = detailIsbn?.let { isbn -> books.firstOrNull { it.isbn == isbn } }

    val reveal: (ShelfBook) -> Unit = reveal@{ shelfBook ->
        if (phase != Phase.Idle) return@reveal
        phase = Phase.Flying
        flightBook = shelfBook
        flyTarget = target.snapped()
        flyStart = bounds[shelfBook.key]
            ?.takeIf { it.width > 1f && it.height > 1f }
            ?.snapped()
            ?: fallbackStart(target).snapped()

        animJob = scope.launch {
            try {
                bounds[shelfBook.key]
                    ?.takeIf { it.width > 1f && it.height > 1f }
                    ?.let { flyStart = it.snapped() }

                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 620,
                        easing = CubicBezierEasing(0.18f, 0.86f, 0.10f, 1f)
                    )
                )
                detailIsbn = shelfBook.key
                detailVisible = true
                phase = Phase.Detail
            } catch (_: CancellationException) {
                // iptal: durum, iptal eden tarafindan kurulur
            }
        }
    }

    val resetFlight: () -> Unit = {
        flightBook = null
        flyStart = null
        flyTarget = null
    }

    val dismiss: () -> Unit = dismiss@{
        if (phase != Phase.Detail) return@dismiss
        phase = Phase.Closing
        animJob = scope.launch {
            detailVisible = false
            delay(150)
            try {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing)
                )
            } catch (_: CancellationException) {
            } finally {
                detailIsbn = null
                flightBook = null
                flyStart = null
                flyTarget = null
                phase = Phase.Idle
            }
        }
    }

    val handleScan: (String) -> Unit = { raw ->
        val isbn = normalizeIsbn(raw)
        if (isbn == null) {
            scope.launch { snackbarHost.showSnackbar("Bu barkod bir kitap ISBN'i değil") }
        } else {
            scannerOpen = false
            scope.launch {
                val found = vm.find(isbn)
                if (found != null) {
                    tab = 0
                    delay(200)
                    shelfByIsbn[found.isbn]?.let(reveal)
                } else {
                    unknownIsbn = isbn
                }
            }
        }
    }

    BackHandler(enabled = phase != Phase.Idle || scannerOpen || unknownIsbn != null) {
        when {
            unknownIsbn != null -> unknownIsbn = null
            scannerOpen -> scannerOpen = false
            phase == Phase.Flying || phase == Phase.Closing -> {
                animJob?.cancel()
                scope.launch {
                    progress.snapTo(0f)
                    resetFlight()
                    detailIsbn = null
                    detailVisible = false
                    phase = Phase.Idle
                }
            }
            phase == Phase.Detail -> dismiss()
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHost) },
            bottomBar = {
                if (phase == Phase.Idle && !scannerOpen) {
                    LibraryBar(tab = tab, onTab = { tab = it })
                }
            },
            floatingActionButton = {
                if (phase == Phase.Idle && !scannerOpen && tab == 0) {
                    ExtendedFloatingActionButton(
                        onClick = { scannerOpen = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                        text = { Text("Tara") }
                    )
                }
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                when (tab) {
                    0 -> {
                        ShelfHeader(realCount = books.size, spineCount = shelfRows.sumOf { it.books.size })
                        Bookshelf(
                            rows = shelfRows,
                            interactive = phase == Phase.Idle,
                            onRealBookClick = reveal,
                            onBounds = { book, rect -> bounds[book.key] = rect },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    else -> BookListTab(
                        books = books,
                        onBookClick = { entity ->
                            tab = 0
                            scope.launch {
                                delay(120)
                                shelfByIsbn[entity.isbn]?.let(reveal)
                            }
                        }
                    )
                }
            }
        }

        // Ucus katmani yalnizca ucus/kapanis sirasinda cizilir; detay oturunca
        // detay katmani kapağı devralir (cift cizim ve artik golge olmaz).
        if (flightBook != null && !detailVisible) {
            FlightLayer(
                book = flightBook!!,
                start = flyStart ?: fallbackStart(target).snapped(),
                target = flyTarget ?: target.snapped(),
                progress = progress
            )
        }

        if (detailBook != null) {
            BookDetailLayer(
                book = detailBook,
                target = flyTarget ?: target.snapped(),
                visible = detailVisible,
                onClose = dismiss
            )
        }

        if (scannerOpen) {
            ScannerOverlay(
                onClose = { scannerOpen = false },
                onIsbn = handleScan
            )
        }

        unknownIsbn?.let { isbn ->
            AddBookSheet(
                isbn = isbn,
                onDismiss = { unknownIsbn = null },
                onAdd = {
                    unknownIsbn = null
                    scope.launch {
                        val added = vm.add(isbn)
                        snackbarHost.showSnackbar(
                            if (added) "Rafa eklendi · kapak birazdan gelir"
                            else "Bu kitap zaten kütüphanede"
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ShelfHeader(realCount: Int, spineCount: Int) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)) {
        Text(
            text = "Ender'in Kütüphanesi",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Rafta $spineCount cilt · kütüphanende $realCount kitap",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryBar(tab: Int, onTab: (Int) -> Unit) {
    NavigationBar(containerColor = Color(0xFFFFFAF2), tonalElevation = 0.dp) {
        NavigationBarItem(
            selected = tab == 0,
            onClick = { onTab(0) },
            icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
            label = { Text("Raf") },
            colors = navColors()
        )
        NavigationBarItem(
            selected = tab == 1,
            onClick = { onTab(1) },
            icon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null) },
            label = { Text("Kitaplarım") },
            colors = navColors()
        )
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = Color(0x22B4683C),
    unselectedIconColor = Color(0xFF9C8B76),
    unselectedTextColor = Color(0xFF9C8B76)
)

@Composable
private fun BookListTab(books: List<BookEntity>, onBookClick: (BookEntity) -> Unit) {
    if (books.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Henüz kitap eklemedin", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Raf sekmesindeki “Tara” ile kitabın barkodunu okut.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.isbn }) { book ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onBookClick(book) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCover(
                    book = book,
                    modifier = Modifier.width(48.dp).aspectRatio(2f / 3f),
                    corner = 6.dp,
                    compact = true
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (book.authors.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = book.authors,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBookSheet(
    isbn: String,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 40.dp)
        ) {
            Text("Kütüphanende yok", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Bu kitabı rafa ekleyelim mi? Kapak ve künye bilgisi internetten gelecek, gerisi çevrimdışı çalışır.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = isbn,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Rafa ekle")
            }
        }
    }
}

@Composable
private fun targetCoverRect(): Rect {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    val top = with(density) { (statusTop + 26.dp).toPx() }
    val bottomReserve = with(density) { (navBottom + 210.dp).toPx() }

    val maxWidth = min(screenWidthPx * 0.64f, with(density) { 330.dp.toPx() })
    val maxHeight = (screenHeightPx - top - bottomReserve)
        .coerceAtLeast(with(density) { 190.dp.toPx() })

    val width = min(maxWidth, maxHeight / 1.5f)
    val height = width * 1.5f
    val left = (screenWidthPx - width) / 2f
    return Rect(left = left, top = top, right = left + width, bottom = top + height)
}

private fun fallbackStart(target: Rect): Rect = Rect(
    left = target.center.x - target.width * 0.07f,
    top = target.center.y - target.height * 0.16f,
    right = target.center.x + target.width * 0.07f,
    bottom = target.center.y + target.height * 0.16f
)
