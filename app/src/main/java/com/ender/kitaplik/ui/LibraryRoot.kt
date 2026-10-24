package com.ender.kitaplik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ender.kitaplik.data.BookEntity
import com.ender.kitaplik.data.RemoteMeta
import com.ender.kitaplik.data.normalizeIsbn
import com.ender.kitaplik.ui.shelf.Bookshelf
import com.ender.kitaplik.ui.shelf.ShelfBook
import com.ender.kitaplik.ui.shelf.ShelfRow
import com.ender.kitaplik.ui.shelf.buildFittedShelves
import com.ender.kitaplik.ui.shelf.rankMatches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Phase { Idle, Flying, Detail, Closing }

private data class PendingLookup(
    val isbn: String,
    val loading: Boolean,
    val meta: RemoteMeta?,
    /** Ayni eserin kutuphanede bulunan baska baskisi. */
    val match: BookEntity? = null,
    val checkingEditions: Boolean = false
)

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
    var manualEntry by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingLookup?>(null) }

    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var flightBook by remember { mutableStateOf<ShelfBook?>(null) }
    var flyStart by remember { mutableStateOf<Rect?>(null) }
    var flyTarget by remember { mutableStateOf<Rect?>(null) }
    // Ucus katmani, progress sifira cekilip ilk kare hazir olana kadar cizilmez.
    // Aksi halde Animatable'in onceki turdan kalan degeri (1f) bir kare boyunca
    // gorunup "raf bir anda karardi" etkisi veriyordu.
    var flightArmed by remember { mutableStateOf(false) }
    var detailIsbn by rememberSaveable { mutableStateOf<String?>(null) }
    var detailVisible by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(Phase.Idle) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    val progress = remember { Animatable(0f) }

    var searching by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val searchMatches = remember(books, query) {
        if (query.isBlank()) emptyList() else rankMatches(books, query)
    }
    val matchedKeys = remember(searchMatches) { searchMatches.map { it.key }.toSet() }

    val closeSearch: () -> Unit = {
        searching = false
        query = ""
    }

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

    // Raf gorunumu tek bir degismez nesnede toplanir: ucus/detay durumu degistiginde
    // raf alt agaci recompose edilmez (180 sirt yeniden cizilip animasyonu kilitliyordu).
    val shelfVisual = remember(shelfRows, matchedKeys, searching, query) {
        ShelfVisual(
            rows = shelfRows,
            matched = if (searching) matchedKeys else emptySet(),
            searching = searching && query.isNotBlank()
        )
    }
    val detailBook = detailIsbn?.let { isbn -> books.firstOrNull { it.isbn == isbn } }

    val reveal: (ShelfBook) -> Unit = reveal@{ shelfBook ->
        if (phase != Phase.Idle) return@reveal
        phase = Phase.Flying
        flightArmed = false
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

                // Ilk kare bedava degil: ucus katmani o an kuruluyor ve kapak dosyadan
                // cozuluyor. Animasyonu hemen baslatirsak bu is tek karede birikip
                // progress'i 0'dan ~0.9'a firlatiyor (gorsel: sicrama). Iki kare bekle:
                // takilma p=0 iken (kapak henuz alfa 0) yasanir, sonra akici baslar.
                progress.snapTo(0f)
                withFrameNanos { }
                withFrameNanos { }
                flightArmed = true
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 900,
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
        flightArmed = false
        flightBook = null
        flyStart = null
        flyTarget = null
    }

    // Rafin ucus sirasinda recompose olmamasi icin lambda kimlikleri sabitlenir.
    // (Aksi halde 180 sirtin tamami yeniden cizilir ve animasyonun ilk karesi kilitlenir.)
    val revealLatest = rememberUpdatedState<(ShelfBook) -> Unit>(reveal)
    val stableReveal: (ShelfBook) -> Unit = remember { { b -> revealLatest.value(b) } }
    val stableBounds: (ShelfBook, Rect) -> Unit = remember {
        { b, r -> bounds[b.key] = r }
    }

    val dismiss: () -> Unit = dismiss@{
        if (phase != Phase.Detail) return@dismiss
        phase = Phase.Closing
        // Kapanista ucus katmani devralir; armed olmali yoksa katman hic cizilmez.
        flightArmed = true
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

    // Kitapligi temizleme: detay kapanir, kayit silinir, 4 sn "Geri al" sunulur.
    val removeBook: (BookEntity) -> Unit = remove@{ entity ->
        if (phase != Phase.Detail) return@remove
        dismiss()
        scope.launch {
            delay(320)
            vm.remove(entity.isbn)
            val result = snackbarHost.showSnackbar(
                message = "“${entity.title}” raftan çıkarıldı",
                actionLabel = "Geri al",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                vm.restore(entity)
            }
        }
    }

    val handleScan: (String) -> Unit = { raw ->
        val isbn = normalizeIsbn(raw)
        if (isbn == null) {
            scope.launch { snackbarHost.showSnackbar("Bu barkod bir kitap ISBN'i değil") }
        } else {
            scannerOpen = false
            manualEntry = false
            scope.launch {
                val found = vm.find(isbn)
                if (found != null) {
                    tab = 0
                    // Sirtin ekran koordinati olusana kadar bekle; yoksa ucus
                    // raf yerine dogrudan hedefte baslar ve animasyon gorunmez.
                    var waited = 0
                    while (waited < 1500 && (bounds[found.isbn]?.width ?: 0f) <= 1f) {
                        delay(50)
                        waited += 50
                    }
                    delay(60)
                    shelfByIsbn[found.isbn]?.let(reveal)
                } else {
                    // Kutuphanede yok: once Google Books, olmazsa Open Library
                    pending = PendingLookup(isbn = isbn, loading = true, meta = null)
                    val meta = vm.lookup(isbn)
                    pending = PendingLookup(isbn = isbn, loading = false, meta = meta)

                    // ISBN farkli olsa da ayni eserin baska baskisi rafta olabilir
                    if (meta != null) {
                        pending = pending?.copy(checkingEditions = true)
                        val match = vm.findEdition(isbn, meta)
                        pending?.takeIf { it.isbn == isbn }?.let {
                            pending = it.copy(match = match, checkingEditions = false)
                        }
                    }
                }
            }
        }
    }

    BackHandler(
        enabled = phase != Phase.Idle || scannerOpen || pending != null || manualEntry || searching
    ) {
        when {
            searching -> closeSearch()
            pending != null -> pending = null
            manualEntry -> manualEntry = false
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
                // Alt bar her zaman olculur, sadece alfa ile gizlenir. Kosullu olarak
                // kaldirildiginda Scaffold innerPadding'i degisiyor, tum ekran (180 sirt)
                // yeniden olculuyordu; ucusun ilk karesi bu yuzden kilitleniyordu.
                Box(
                    Modifier.graphicsLayer {
                        alpha = if (phase == Phase.Idle && !scannerOpen) 1f else 0f
                    }
                ) {
                    LibraryBar(tab = tab, onTab = { tab = it })
                }
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Ucus sirasinda raf bir RenderNode'a kaydedilir ve her karede
                    // yeniden cizilmez: 180 sirt (Canvas + donuk metin) cizimi kare
                    // butcesini asiyordu. Raf gorunur kalir, overlay uzerine biner.
                    .graphicsLayer { }
            ) {
                when (tab) {
                    0 -> {
                        ShelfHeader(
                            realCount = books.size,
                            spineCount = shelfRows.sumOf { it.books.size },
                            onScan = { scannerOpen = true },
                            searching = searching,
                            query = query,
                            onQueryChange = { query = it },
                            onToggleSearch = {
                                if (searching) closeSearch() else searching = true
                            },
                            onSubmit = {
                                val first = searchMatches.firstOrNull()
                                if (first != null) {
                                    closeSearch()
                                    shelfByIsbn[first.key]?.let(reveal)
                                } else {
                                    scope.launch {
                                        val result = snackbarHost.showSnackbar(
                                            message = "Kütüphanende bulunamadı",
                                            actionLabel = "ISBN ile ekle",
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            closeSearch()
                                            manualEntry = true
                                        }
                                    }
                                }
                            }
                        )
                        ShelfSection(
                            visual = shelfVisual,
                            onReveal = stableReveal,
                            onBounds = stableBounds
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
        if (flightBook != null && !detailVisible && flightArmed) {
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
                onClose = dismiss,
                onRemove = { removeBook(detailBook) },
                onRetryEnrich = {
                    val isbn = detailBook.isbn
                    scope.launch { vm.retryEnrich(isbn) }
                }
            )
        }

        if (scannerOpen) {
            ScannerOverlay(
                onClose = { scannerOpen = false },
                onManualEntry = {
                    scannerOpen = false
                    manualEntry = true
                },
                onIsbn = handleScan
            )
        }

        if (manualEntry) {
            ManualIsbnDialog(
                onDismiss = { manualEntry = false },
                onSubmit = { isbn -> handleScan(isbn) }
            )
        }

        pending?.let { lookup ->
            AddBookSheet(
                lookup = lookup,
                onDismiss = { pending = null },
                onOpenMatch = { entity ->
                    pending = null
                    tab = 0
                    scope.launch {
                        delay(220)
                        shelfByIsbn[entity.isbn]?.let(reveal)
                    }
                },
                onAdd = {
                    pending = null
                    scope.launch {
                        val added = vm.add(lookup.isbn, lookup.meta)
                        snackbarHost.showSnackbar(
                            if (added) "Rafa eklendi" else "Bu kitap zaten kütüphanede"
                        )
                    }
                }
            )
        }
    }
}

/** Rafin cizim girdileri; tek degismez nesne -> ucus sirasinda recompose edilmez. */
@Immutable
private data class ShelfVisual(
    val rows: List<ShelfRow>,
    val matched: Set<String>,
    val searching: Boolean
)

/**
 * Rafi ana ekrandan ayirir: parametreleri sabit oldugu icin ucus/detay durumu
 * degistiginde (phase, flyStart, detailVisible...) bu alt agac atlanir.
 */
@Composable
private fun ColumnScope.ShelfSection(
    visual: ShelfVisual,
    onReveal: (ShelfBook) -> Unit,
    onBounds: (ShelfBook, Rect) -> Unit
) {
    Bookshelf(
        rows = visual.rows,
        interactive = true,
        onRealBookClick = onReveal,
        onBounds = onBounds,
        matchedKeys = visual.matched,
        searching = visual.searching,
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun ShelfHeader(
    realCount: Int,
    spineCount: Int,
    onScan: () -> Unit,
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSubmit: () -> Unit
) {
    // Arama modunda baslik alani ayni yukseklikte kalir; raf yer degistirmez.
    if (searching) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Kitap veya yazar ara",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF8A6A48)
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFFFFBF3),
                    unfocusedContainerColor = Color(0xFFFFFBF3),
                    focusedBorderColor = Color(0xFFB08A55),
                    unfocusedBorderColor = Color(0x336B4A2F),
                    focusedTextColor = Color(0xFF3A2E24),
                    unfocusedTextColor = Color(0xFF3A2E24),
                    cursorColor = Color(0xFF8A5A2B)
                )
            )
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Aramayı kapat",
                    tint = Color(0xFF6B4A2F)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Ender Kütüphane",
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Rafta $spineCount cilt · kütüphanende $realCount kitap",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0x1F6B4A2F))
                        .clickable(onClick = onToggleSearch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Kitaplıkta ara",
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFF6B4A2F)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = onScan,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFFEADCC4),
                contentColor = Color(0xFF6B4A2F)
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCodeScanner,
                contentDescription = "Tara",
                modifier = Modifier.size(24.dp)
            )
        }
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
    lookup: PendingLookup,
    onDismiss: () -> Unit,
    onOpenMatch: (BookEntity) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = if (lookup.match != null) "Bu baskı rafta yok" else "Kütüphanende yok",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(14.dp))

            // Ayni eserin baska baskisi zaten kutuphanede
            lookup.match?.let { existing ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Aynı eserin başka bir baskısı kütüphanende",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCover(
                            book = existing,
                            modifier = Modifier.width(52.dp).aspectRatio(2f / 3f),
                            corner = 6.dp,
                            compact = true
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = existing.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (existing.authors.isNotBlank()) {
                                Text(
                                    text = existing.authors,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = listOfNotNull(
                                    existing.publishedDate.takeIf { it.isNotBlank() },
                                    existing.publisher.takeIf { it.isNotBlank() },
                                    "ISBN ${existing.isbn}"
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { onOpenMatch(existing) },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("Mevcut baskıyı aç")
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            val meta = lookup.meta
            when {
                lookup.loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Google Books'ta aranıyor…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                meta != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BookCover(
                            book = BookEntity(
                                isbn = lookup.isbn,
                                title = meta.title,
                                authors = meta.authors,
                                publisher = meta.publisher,
                                publishedDate = meta.publishedDate,
                                pageCount = meta.pageCount,
                                description = meta.description
                            ),
                            modifier = Modifier.width(62.dp).aspectRatio(2f / 3f),
                            corner = 6.dp,
                            compact = true
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = meta.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (meta.authors.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = meta.authors,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (meta.publisher.isNotBlank() || meta.publishedDate.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = listOf(meta.publisher, meta.publishedDate)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Kaynak: ${meta.source.ifBlank { "Google Books" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                else -> {
                    Text(
                        text = "Künye bulunamadı (Google Books ve Open Library). Yine de ISBN ile rafa ekleyebilirsin, bilgiler sonra tamamlanır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (lookup.checkingEditions) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Aynı eserin diğer baskıları kontrol ediliyor…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "ISBN ${lookup.isbn}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAdd,
                enabled = !lookup.loading,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    when {
                        lookup.match != null -> "Bu baskıyı yine de ekle"
                        meta != null -> "Rafa ekle"
                        else -> "ISBN ile ekle"
                    }
                )
            }
        }
    }
}

@Composable
private fun ManualIsbnDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val normalized = normalizeIsbn(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("ISBN ile ara", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                Text(
                    text = "Barkod okunamadıysa kitabın arkasındaki ISBN numarasını yaz. 10 veya 13 hane olabilir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() || it == 'X' || it == 'x' }
                    },
                    singleLine = true,
                    label = { Text("ISBN") },
                    placeholder = { Text("978…") },
                    isError = text.length >= 10 && normalized == null,
                    supportingText = {
                        when {
                            text.isEmpty() -> Text("Örn. 9789750718533")
                            normalized == null -> Text("Geçersiz ISBN")
                            else -> Text("Geçerli: $normalized")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { normalized?.let(onSubmit) },
                enabled = normalized != null
            ) { Text("Sorgula") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
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
