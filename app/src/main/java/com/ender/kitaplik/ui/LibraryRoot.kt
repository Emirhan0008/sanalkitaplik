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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ender.kitaplik.BuildConfig
import com.ender.kitaplik.data.BookEntity
import com.ender.kitaplik.data.BookMetadataSource
import com.ender.kitaplik.data.RemoteMeta
import com.ender.kitaplik.data.SearchCandidate
import com.ender.kitaplik.data.WorkMatcher
import com.ender.kitaplik.data.normalizeIsbn
import com.ender.kitaplik.ui.shelf.Bookshelf
import com.ender.kitaplik.ui.shelf.ShelfBook
import com.ender.kitaplik.ui.shelf.ShelfRow
import com.ender.kitaplik.ui.shelf.buildFittedShelves
import com.ender.kitaplik.ui.shelf.rankMatches
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Phase { Idle, Flying, Detail, Closing }

private data class PendingLookup(
    val isbn: String,
    val loading: Boolean,
    val meta: RemoteMeta?,
    /** Ayni eserin kutuphanede bulunan baska baskisi. */
    val match: BookEntity? = null,
    val checkingEditions: Boolean = false,
    /** Kunye bulunamadiysa sebebi: "kayit yok" mu, "kaynaklara ulasilamadi" mi? */
    val outcome: BookMetadataSource.FetchOutcome? = null
)

/** Isimle arama oturumu: OCR'dan gelen ad/yazar + cevrimici aday listesi. */
private data class TitleSearch(
    val title: String,
    val author: String,
    val loading: Boolean,
    val results: List<SearchCandidate> = emptyList()
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
    val appContext = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }

    // Yedekleme: ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT (paylasim menusu degil).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching { vm.exportBackup(uri) }
            snackbarHost.showSnackbar(
                result.fold(
                    onSuccess = { count -> if (count == 0) "Kütüphane boş" else "$count kitap yedeklendi" },
                    onFailure = { "Yedek alınamadı: ${it.message ?: "bilinmeyen hata"}" }
                )
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching { vm.importBackup(uri) }
            snackbarHost.showSnackbar(
                result.fold(
                    onSuccess = { it.message },
                    onFailure = { "Yedek okunamadı: ${it.message ?: "bilinmeyen hata"}" }
                )
            )
        }
    }
    val requestExportImpl: () -> Unit = {
        exportLauncher.launch("ender-kutuphane-yedek-${backupStamp()}.json")
    }
    val requestExportLatest = rememberUpdatedState(requestExportImpl)
    val requestExport: () -> Unit = remember { { requestExportLatest.value() } }
    val requestImportImpl: () -> Unit = {
        // Bazi dosya yoneticileri JSON'u text/plain olarak isaretler.
        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }
    val requestImportLatest = rememberUpdatedState(requestImportImpl)
    val requestImport: () -> Unit = remember { { requestImportLatest.value() } }

    // Ayarlar sekmesi: kaynak sagligi. Devre kesici dolunca durum otomatik
    // "aktif" olur; 15 sn'de bir yeniden okunur.
    var sourceHealth by remember { mutableStateOf(vm.sourceHealth()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            sourceHealth = vm.sourceHealth()
        }
    }
    val versionLabel = remember { "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" }

    var tab by rememberSaveable { mutableStateOf(0) }
    // Sekme gecisi gercek capraz solma: 0 = Raf, 1 = Kitaplarim, 2 = Ayarlar.
    // Raf alt agaci HIC dispose edilmez (180 sirt yeniden kurulunca ~1 sn ekran
    // bos kaliyordu); yalnizca alfa ile solar. Diger katmanlar kendi alfalari
    // dustugunde atilir. Dokunmayi yalniz tam gorunur (alfa = 1) katman alir.
    val tabAlphas = remember { List(3) { Animatable(if (it == tab) 1f else 0f) } }
    // Dokunulabilirlik karari tiklama ANINDA okunur (kararli lambda). Parametre
    // kimligi sabit kaldigi icin sekme gecislerinde "interactive" degisimi
    // 180 sirtin tamamini yeniden kurmaz (olculen: ~90 ms COMP + ~90 ms GPU).
    val shelfInteractive: () -> Boolean = remember { { tabAlphas[0].value == 1f } }
    val listInteractive: () -> Boolean = remember { { tabAlphas[1].value == 1f } }
    val switchTab: suspend (Int) -> Unit = switchTab@{ newTab ->
        if (newTab == tab) return@switchTab
        val oldTab = tab
        tab = newTab
        coroutineScope {
            launch { tabAlphas[oldTab].animateTo(0f, tween(160)) }
            launch { tabAlphas[newTab].animateTo(1f, tween(220)) }
        }
    }
    var scannerOpen by rememberSaveable { mutableStateOf(false) }
    var manualEntry by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingLookup?>(null) }
    var titleSearch by remember { mutableStateOf<TitleSearch?>(null) }

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

    val closeSearchImpl: () -> Unit = { searching = false; query = "" }
    val closeSearchLatest = rememberUpdatedState(closeSearchImpl)
    val closeSearch: () -> Unit = remember { { closeSearchLatest.value() } }

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
    // Yeni kitap eklenince liste yeniden kurulur; coroutine'ler kapanima alinan
    // bayat haritayi degil her zaman en guncelini okumali.
    val shelfByIsbnLatest by rememberUpdatedState(shelfByIsbn)

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

    val revealImpl: (ShelfBook) -> Unit = reveal@{ shelfBook ->
        if (phase != Phase.Idle) return@reveal
        phase = Phase.Flying
        // Kitap rafdan kalkarken hafif tik: ucusun basladigini hissettirir.
        appContext.tickHaptic(24)
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

                // KAPAK ON-YUKLEME: Coil dosyayi diskten cozmeden ucus baslamasin.
                // Ucus katmani AsyncImage ile kapagi cizer; eger cozum henuz
                // bitmediyse ilk karelerde kutu bos kaliyordu -> siyah ekran.
                // Boylece ucusun ILK karesinde kapak gercekten hazirdir.
                shelfBook.entity?.coverPath?.let { path ->
                    val req = ImageRequest.Builder(appContext)
                        .data(File(path))
                        .size(
                            target.width.roundToInt().coerceAtLeast(1),
                            target.height.roundToInt().coerceAtLeast(1)
                        )
                        .build()
                    runCatching { appContext.imageLoader.execute(req) }
                }

                progress.snapTo(0f)
                withFrameNanos { }
                flightArmed = true
                // Kunye icerigini ucusun BASINDA, gorunmez halde kur: metin olcumu +
                // kapak yerlesimi (~55 ms COMP + ~57 ms layout) ucus perdesinin
                // arkasinda odenir; kapak yerine otururken takinti kalmaz, icerik
                // yalnizca alfa ile yumusak girer (BookDetailLayer: visible=false iken
                // butun katmanlar alfa 0'dir, hicbir sey cizilmez).
                detailIsbn = shelfBook.key
                progress.animateTo(
                    targetValue = 1f,
                    // 900 ms cok yavasti ("1 sn siyah ekran" hissi). 320 ms + yumusak
                    // cikis: hizli ama carpma yok.
                    animationSpec = tween(
                        durationMillis = 320,
                        easing = CubicBezierEasing(0.20f, 0.90f, 0.25f, 1f)
                    )
                )
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
    // NOT: Bu sabitleme yalniz raf icin degil; reveal/dismiss/removeBook/handleScan
    // gibi govde icinde yeniden uretilen tum geri cagirmalar icin gecerli. Kimligi
    // degisen her lambda, onu tasiyan alt agacin (raf basligi, kitap listesi,
    // kunye) "atlanamaz" olmasina ve her durum yaziminda yeniden kurulmasina yol
    // aciyordu: olculen maliyet ~50-65 ms yeniden kurulum + ~50 ms yeniden yerlesim.
    val revealLatest = rememberUpdatedState(revealImpl)
    val reveal: (ShelfBook) -> Unit = remember { { b -> revealLatest.value(b) } }
    val stableBounds: (ShelfBook, Rect) -> Unit = remember {
        { b, r -> bounds[b.key] = r }
    }

    val dismissImpl: () -> Unit = dismiss@{
        if (phase != Phase.Detail) return@dismiss
        phase = Phase.Closing
        // Kapanista ucus katmani devralir; armed olmali yoksa katman hic cizilmez.
        flightArmed = true
        animJob = scope.launch {
            detailVisible = false
            // Yapay bekleme yoktu (eski kod 150 ms "delay" ile kapanisi geciktiriyordu).
            // Ucusun zemini artik detay zeminiyle ayni krem tonunda oldugundan
            // devir anliktir; kapak da zaten yerinde durur.
            try {
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
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
    val dismissLatest = rememberUpdatedState(dismissImpl)
    val dismiss: () -> Unit = remember { { dismissLatest.value() } }

    // Kitapligi temizleme: detay kapanir, kayit silinir, 4 sn "Geri al" sunulur.
    val removeBookImpl: (BookEntity) -> Unit = remove@{ entity ->
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
    val removeBookLatest = rememberUpdatedState(removeBookImpl)
    val removeBook: (BookEntity) -> Unit = remember { { e -> removeBookLatest.value(e) } }

    val handleScanImpl: (String) -> Unit = { raw ->
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
                    pending = PendingLookup(
                        isbn = isbn,
                        loading = false,
                        meta = meta,
                        outcome = vm.fetchOutcome()
                    )

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
    val handleScanLatest = rememberUpdatedState(handleScanImpl)
    val handleScan: (String) -> Unit = remember { { s -> handleScanLatest.value(s) } }

    BackHandler(
        enabled = phase != Phase.Idle || scannerOpen || pending != null || manualEntry ||
            searching || titleSearch != null
    ) {
        when {
            titleSearch != null -> titleSearch = null
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
            // Snackbar klavyenin ustunde kalmali: klavye acikken eylem dugmeleri
            // (ör. "ISBN ile ekle") IME'nin arkasinda kaliyordu.
            snackbarHost = { SnackbarHost(snackbarHost, modifier = Modifier.imePadding()) },
            bottomBar = {
                // Alt bar her zaman olculur, sadece alfa ile gizlenir. Kosullu olarak
                // kaldirildiginda Scaffold innerPadding'i degisiyor, tum ekran (180 sirt)
                // yeniden olculuyordu; ucusun ilk karesi bu yuzden kilitleniyordu.
                Box(
                    Modifier.graphicsLayer {
                        alpha = if (phase == Phase.Idle && !scannerOpen) 1f else 0f
                    }
                ) {
                    LibraryBar(
                        tab = tab,
                        onTab = { newTab -> scope.launch { switchTab(newTab) } }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(Modifier.weight(1f)) {
                    // Raf katmani: hic dispose edilmez, yalnizca alfa ile solar.
                    // Ucus sirasinda raf bir RenderNode'a kaydedilir ve her karede
                    // yeniden cizilmez: 180 sirt (Canvas + donuk metin) cizimi kare
                    // butcesini asiyordu. Raf gorunur kalir, overlay uzerine biner.
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Kunye acikken raf tamamen kapatilir: detay zemini zaten
                                // opak oldugu icin rafin cizimi saf israfti (olcu: GPU
                                // 17 ms -> 3 ms, Slow issue draw commands 6/6 -> 1/6).
                                // Alfa 0 = alt agac hic cizilmez; yeniden kurulum da olmaz.
                                // Ucus perdesi opaklastiginda da ayni sebep gecerli:
                                // perde p=0.50'de tam opak oldugu icin raf gorunmuyor.
                                // (Esik, FlightLayer'daki smooth(0.05f, 0.50f, p) ile
                                // birebir ayni olmali; aksi halde raf, perde %100
                                // opaklasmadan gorunur/gorunmez titremesi yapar.)
                                alpha = when {
                                    detailVisible -> 0f
                                    flightArmed && progress.value > 0.50f -> 0f
                                    else -> tabAlphas[0].value
                                }
                            }
                    ) {
                        ShelfHeader(
                            realCount = books.size,
                            spineCount = shelfRows.sumOf { it.books.size },
                            onScan = { scannerOpen = true },
                            onExport = requestExport,
                            onImport = requestImport,
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
                            // Raf tamamen gorunurken dokunulabilir; baska sekmedeyken
                            // alfa 0 olsa bile dokunmayi alttaki sirlar almasin.
                            // Karar tiklama aninda okunur -> parametre kimligi sabit.
                            interactive = shelfInteractive,
                            onReveal = reveal,
                            onBounds = stableBounds
                        )
                    }

                    // Liste katmani yalnizca geciste / Kitaplarim sekmesinde ayakta;
                    // ustte durur, gecis boyunca dokunmalari o alir.
                    if (tab == 1 || tabAlphas[1].value > 0f) {
                        BookListTab(
                            books = books,
                            // Liste tamamen oturunca dokunulabilir; solma sirasinda
                            // dokunmalar gecisi bolmesin. Karar tiklama aninda okunur.
                            interactive = listInteractive,
                            onBookClick = { entity ->
                                scope.launch {
                                    // Listeden rafa donuste solma yok: secilen kitap
                                    // kendi rafinda hemen gorunsun.
                                    tabAlphas[1].snapTo(0f)
                                    tabAlphas[0].snapTo(1f)
                                    tab = 0
                                    delay(120)
                                    shelfByIsbn[entity.isbn]?.let(reveal)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = tabAlphas[1].value }
                        )
                    }

                    // Ayarlar katmani ayni kuralla: yalnizca geciste / sekmedeyken.
                    if (tab == 2 || tabAlphas[2].value > 0f) {
                        SettingsTab(
                            health = sourceHealth,
                            apiKeyConfigured = BuildConfig.GBOOKS_KEY.isNotBlank(),
                            versionLabel = versionLabel,
                            onClearCache = {
                                scope.launch {
                                    val deleted = vm.clearCoverCache()
                                    snackbarHost.showSnackbar(
                                        if (deleted == 0) "Önbellek zaten boş"
                                        else "$deleted kapak silindi"
                                    )
                                }
                            },
                            onExport = requestExport,
                            onImport = requestImport,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = tabAlphas[2].value }
                        )
                    }
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
                onIsbn = handleScan,
                onTitleSearch = { title, author ->
                    // Tarayici kapanir, isimle arama baslar; sonuc paneli acilir.
                    scannerOpen = false
                    val session = TitleSearch(title = title, author = author, loading = true)
                    titleSearch = session
                    scope.launch {
                        val results = vm.searchByTitle(title, author)
                        titleSearch = titleSearch
                            ?.takeIf { it.title == session.title && it.loading }
                            ?.copy(loading = false, results = results)
                    }
                }
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

        titleSearch?.let { search ->
            // Rafta ayni eser varsa (baski farki olabilir) oncelikle orada gosterilir.
            val localMatches = remember(books, search.title, search.author) {
                books.filter {
                    WorkMatcher.sameWork(search.title, search.author, it.title, it.authors)
                }
            }
            val libraryIsbns = remember(books) { books.mapTo(HashSet()) { b -> b.isbn } }
            TitleSearchSheet(
                state = search,
                localMatches = localMatches,
                libraryIsbns = libraryIsbns,
                onDismiss = { titleSearch = null },
                onOpenLocal = { entity ->
                    titleSearch = null
                    tab = 0
                    scope.launch {
                        delay(220)
                        shelfByIsbnLatest[entity.isbn]?.let(reveal)
                    }
                },
                onPick = { candidate ->
                    val existing = books.firstOrNull { it.isbn == candidate.isbn }
                    titleSearch = null
                    scope.launch {
                        if (existing != null) {
                            tab = 0
                            delay(220)
                            shelfByIsbnLatest[existing.isbn]?.let(reveal)
                        } else {
                            // Prefill yok: tam kunye ISBN'den cekilir.
                            val added = vm.add(candidate.isbn)
                            // Snackbar kendi coroutine'inde: showSnackbar ~4 sn
                            // askiya alir, reveal'i geciktiriyordu.
                            scope.launch {
                                snackbarHost.showSnackbar(
                                    if (added) "Rafa eklendi" else "Bu kitap zaten kütüphanede"
                                )
                            }
                            if (added) {
                                tab = 0
                                // Sirtin ekran koordinati olusana kadar bekle.
                                var waited = 0
                                while (waited < 1500 &&
                                    (bounds[candidate.isbn]?.width ?: 0f) <= 1f
                                ) {
                                    delay(50)
                                    waited += 50
                                }
                                delay(60)
                                shelfByIsbnLatest[candidate.isbn]?.let(reveal)
                            }
                        }
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
    interactive: () -> Boolean,
    onReveal: (ShelfBook) -> Unit,
    onBounds: (ShelfBook, Rect) -> Unit
) {
    Bookshelf(
        rows = visual.rows,
        interactive = interactive,
        onRealBookClick = onReveal,
        onBounds = onBounds,
        matchedKeys = visual.matched,
        searching = visual.searching,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            // Raf kasasinin kenarlarina yumusak vinyet: gozu merkeze ceker,
            // merkezdeki sirlari karartmaz (cizim sonrasi tek kat karartma).
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.radialGradient(
                        0.0f to Color.Transparent,
                        0.52f to Color.Transparent,
                        1.0f to Color(0x33000000),
                        center = center,
                        // Koseye kadar tamamlanan yaricap: kenarlar gercekten kararir.
                        radius = kotlin.math.hypot(center.x, center.y)
                    )
                )
            }
    )
}

@Composable
private fun ShelfHeader(
    realCount: Int,
    spineCount: Int,
    onScan: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
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
                // Alanin sonunda yalnizca ikonlu kucuk arama dugmesi (yazi yok).
                trailingIcon = {
                    IconButton(
                        onClick = onSubmit,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Ara",
                            modifier = Modifier.size(19.dp),
                            tint = Color(0xFF8A5A2B)
                        )
                    }
                },
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
                // Yedekleme menusu basligin yanina konunca baslik kirpiliyordu;
                // alt satirda, arama ikonunun yaninda yer var.
                BackupMenu(onExport = onExport, onImport = onImport)
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

/**
 * Yedekleme menusu (Faz 5). "Yedek al" / "Yedekten yukle".
 * Faz 7'deki ayarlar ekrani gelince buradan oraya tasinabilir.
 */
@Composable
private fun BackupMenu(onExport: () -> Unit, onImport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(26.dp)) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Yedekleme",
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF6B4A2F)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Yedek al (.json)") },
                onClick = {
                    open = false
                    onExport()
                }
            )
            DropdownMenuItem(
                text = { Text("Yedekten yükle") },
                onClick = {
                    open = false
                    onImport()
                }
            )
        }
    }
}

/** Dosya adi icin tarih damgasi: 2026-10-24-1830 */
private fun backupStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
        .format(java.util.Date())

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
        NavigationBarItem(
            selected = tab == 2,
            onClick = { onTab(2) },
            icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            label = { Text("Ayarlar") },
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
private fun BookListTab(
    books: List<BookEntity>,
    onBookClick: (BookEntity) -> Unit,
    interactive: () -> Boolean = { true },
    modifier: Modifier = Modifier
) {
    if (books.isEmpty()) {
        Column(
            modifier
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.isbn }) { book ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { if (interactive()) onBookClick(book) }
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
                        text = when (lookup.outcome) {
                            BookMetadataSource.FetchOutcome.SOURCES_UNREACHABLE ->
                                "Künye kaynaklarına şu anda ulaşılamıyor (Google Books / Open Library). İnternetini kontrol edip tekrar deneyebilirsin; yine de ISBN ile rafa ekleyebilirsin."
                            else ->
                                "Künye bulunamadı (Google Books ve Open Library). Yine de ISBN ile rafa ekleyebilirsin, bilgiler sonra tamamlanır."
                        },
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

/**
 * Isimle arama sonuclari paneli:
 *  - "Rafta": ayni eserin kutuphanedeki baskilari (baski farki olabilir)
 *  - "Veritabaninda": cevrimici adaylar; secilince rafa eklenir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TitleSearchSheet(
    state: TitleSearch,
    localMatches: List<BookEntity>,
    libraryIsbns: Set<String>,
    onDismiss: () -> Unit,
    onOpenLocal: (BookEntity) -> Unit,
    onPick: (SearchCandidate) -> Unit
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
                text = "\u201C${state.title}\u201D i\u00E7in sonu\u00E7lar",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state.author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(18.dp))

            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Aran\u0131yor\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (localMatches.isNotEmpty()) {
                Text(
                    text = "Rafta",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                localMatches.forEach { book ->
                    ResultCard(
                        cover = {
                            BookCover(
                                book = book,
                                modifier = Modifier
                                    .width(52.dp)
                                    .aspectRatio(2f / 3f),
                                corner = 6.dp,
                                compact = true
                            )
                        },
                        title = book.title,
                        authors = book.authors,
                        line = listOfNotNull(
                            book.publisher.takeIf { it.isNotBlank() },
                            "ISBN ${book.isbn}"
                        ).joinToString(" \u00B7 "),
                        badge = null,
                        onClick = { onOpenLocal(book) }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (!state.loading) {
                Text(
                    text = "Veritaban\u0131nda",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                if (state.results.isEmpty() && localMatches.isEmpty()) {
                    Text(
                        text = "Sonu\u00E7 bulunamad\u0131. Ad\u0131 d\u00FCzelterek tekrar deneyebilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.results.forEach { candidate ->
                    ResultCard(
                        cover = { CandidateCover(candidate) },
                        title = candidate.title,
                        authors = candidate.authors,
                        line = listOfNotNull(
                            candidate.publisher.takeIf { it.isNotBlank() },
                            candidate.publishedDate.takeIf { it.isNotBlank() },
                            "ISBN ${candidate.isbn}"
                        ).joinToString(" \u00B7 "),
                        badge = if (candidate.isbn in libraryIsbns) "K\u00FCt\u00FCphanende" else null,
                        onClick = { onPick(candidate) }
                    )
                }
            }
        }
    }
}

/** Ortak sonuc karti: kapak + baslik + yazar + kunye satiri (+ istege bagli rozet). */
@Composable
private fun ResultCard(
    cover: @Composable () -> Unit,
    title: String,
    authors: String,
    line: String,
    badge: String?,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            cover()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (authors.isNotBlank()) {
                    Text(
                        text = authors,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (line.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** Aday kapagi: URL'den yuklenir; kapak yoksa adin ilk harfinden sade bir levha. */
@Composable
private fun CandidateCover(candidate: SearchCandidate) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(52.dp)
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(Color(0xFF241E19)),
        contentAlignment = Alignment.Center
    ) {
        val url = candidate.coverUrl
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = candidate.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = candidate.title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFB08968)
            )
        }
    }
}
