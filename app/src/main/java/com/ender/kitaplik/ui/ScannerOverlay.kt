package com.ender.kitaplik.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text as OcrText
import java.util.concurrent.Executors

/**
 * Tam ekran tarayici. Iki mod:
 *  - Barkod: ilk okunan ISBN'i iletir.
 *  - Kapak adi: OCR kitap adini (ve anlarsa yazari) ayri kutulara doldurur;
 *    kullanici duzenler, "Ara" ile isimle arama baslatir.
 */
@Composable
fun ScannerOverlay(
    onClose: () -> Unit,
    onManualEntry: () -> Unit,
    onIsbn: (String) -> Unit,
    onTitleSearch: (title: String, author: String) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }

    var mode by remember { mutableStateOf(ScanMode.BARCODE) }
    // OCR sonucu kutulara dolar; kullanici bir kutuya dokundugu anda otomatik
    // doldurma durur, elle duzenleme korunur.
    var titleField by remember { mutableStateOf("") }
    var authorField by remember { mutableStateOf("") }
    var ocrEdited by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionAsked = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080706))) {
        if (hasPermission) {
            CameraScannerPreview(
                mode = mode,
                onIsbn = { isbn ->
                    // Barkod okundugunda kisa tik: tarama basarisini elle hissettirir.
                    context.tickHaptic(26)
                    onIsbn(isbn)
                },
                onOcrLines = { lines ->
                    if (!ocrEdited) {
                        val t = pickTitle(lines)
                        val a = pickAuthor(lines, t)
                        if (t.isNotBlank()) titleField = t
                        if (a.isNotBlank()) authorField = a
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (permissionAsked) {
                        "Barkod okumak için kamera izni gerekiyor."
                    } else {
                        "Kamera açılıyor…"
                    },
                    color = Color(0xFFF3E9DA),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (permissionAsked) {
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text("İzin ver") }
                }
            }
        }

        // karartma + tarama cercevesi
        ScanChrome(mode)

        // Mod secimi: barkod / kapak adi
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x33000000))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModePill("Barkod", mode == ScanMode.BARCODE) { mode = ScanMode.BARCODE }
            ModePill("Kapak adı", mode == ScanMode.COVER) { mode = ScanMode.COVER }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (mode) {
                ScanMode.BARCODE -> {
                    Text(
                        text = "Kitabın arkasındaki barkodu çerçeveye hizala",
                        color = Color(0xCCF3E9DA),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onManualEntry,
                        border = BorderStroke(1.dp, Color(0x66F3E9DA)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF3E9DA))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Keyboard,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Elle ISBN gir")
                    }
                }
                ScanMode.COVER -> {
                    CoverSearchPanel(
                        title = titleField,
                        author = authorField,
                        onTitle = { titleField = it; ocrEdited = true },
                        onAuthor = { authorField = it; ocrEdited = true },
                        onRescan = {
                            ocrEdited = false
                            titleField = ""
                            authorField = ""
                        },
                        onSearch = {
                            if (titleField.isNotBlank()) {
                                onTitleSearch(titleField.trim(), authorField.trim())
                            }
                        }
                    )
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x33000000))
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Kapat", tint = Color(0xFFF3E9DA))
        }
    }
}

@Composable
private fun ScanChrome(mode: ScanMode) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Barkod: yatay cerceve; kapak adi: dikey (portre) cerceve.
            val frameWidth = if (mode == ScanMode.COVER) size.width * 0.74f else size.width * 0.78f
            val frameHeight =
                if (mode == ScanMode.COVER) frameWidth * 1.35f else frameWidth * 0.52f
            val left = (size.width - frameWidth) / 2f
            val top = size.height * 0.34f
            val right = left + frameWidth
            val bottom = top + frameHeight

            // cerceve disi karartma
            drawRect(
                color = Color(0xCC080706),
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(size.width, top)
            )
            drawRect(
                color = Color(0xCC080706),
                topLeft = Offset(0f, bottom),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom)
            )
            drawRect(
                color = Color(0xCC080706),
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(left, frameHeight)
            )
            drawRect(
                color = Color(0xCC080706),
                topLeft = Offset(right, top),
                size = androidx.compose.ui.geometry.Size(size.width - right, frameHeight)
            )

            val corner = 42f
            val accent = Color(0xFFD9A05B)
            val stroke = Stroke(width = 5f, cap = StrokeCap.Round)

            fun line(from: Offset, to: Offset) = drawLine(accent, from, to, strokeWidth = 5f, cap = StrokeCap.Round)

            // sol ust
            line(Offset(left, top + corner), Offset(left, top))
            line(Offset(left, top), Offset(left + corner, top))
            // sag ust
            line(Offset(right - corner, top), Offset(right, top))
            line(Offset(right, top), Offset(right, top + corner))
            // sol alt
            line(Offset(left, bottom - corner), Offset(left, bottom))
            line(Offset(left, bottom), Offset(left + corner, bottom))
            // sag alt
            line(Offset(right - corner, bottom), Offset(right, bottom))
            line(Offset(right, bottom), Offset(right, bottom - corner))

            // hafif tarama cizgisi
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, Color(0x66D9A05B), Color.Transparent)
                ),
                start = Offset(left + 12f, top + frameHeight * 0.5f),
                end = Offset(right - 12f, top + frameHeight * 0.5f),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
private fun CameraScannerPreview(
    mode: ScanMode,
    onIsbn: (String) -> Unit,
    onOcrLines: (List<OcrLine>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val released = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val currentOnIsbn by rememberUpdatedState(onIsbn)
    val currentOnOcrLines by rememberUpdatedState(onOcrLines)
    val analyzer = remember {
        ScanAnalyzer(
            onIsbn = { value -> currentOnIsbn(value) },
            onOcrLines = { lines -> currentOnOcrLines(lines) }
        )
    }
    LaunchedEffect(mode) { analyzer.mode = mode }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    if (released.get()) return@addListener
                    runCatching {
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        @Suppress("DEPRECATION")
                        val analysis = ImageAnalysis.Builder()
                            // Daha buyuk cozunurluk: kapaktaki kucuk yazi da okunsun.
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) }

                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        IconButton(
            onClick = {
                torchOn = !torchOn
                camera?.cameraControl?.enableTorch(torchOn)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(24.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x44000000))
        ) {
            Icon(
                imageVector = if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                contentDescription = "Flaş",
                tint = Color(0xFFF3E9DA)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            released.set(true)
            runCatching { camera?.cameraControl?.enableTorch(false) }
            runCatching {
                ProcessCameraProvider.getInstance(context).addListener({
                    runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                }, ContextCompat.getMainExecutor(context))
            }
            runCatching { analyzer.close() }
            executor.shutdown()
        }
    }
}

/** Tarama modu: barkod (ISBN) ya da kapak adi (OCR). */
enum class ScanMode { BARCODE, COVER }

/** OCR'dan cikan tek bir yazi satiri (yukseklik, aday sehiminde kullanilir). */
data class OcrLine(val text: String, val height: Int)

/** Mod basina tek analizci: barkod ya da OCR. */
private class ScanAnalyzer(
    private val onIsbn: (String) -> Unit,
    private val onOcrLines: (List<OcrLine>) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    var mode: ScanMode = ScanMode.BARCODE

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
            .build()
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @Volatile
    private var busy = false

    @Volatile
    private var lastValue: String? = null

    @Volatile
    private var lastAt: Long = 0L

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (mode == ScanMode.COVER) {
            analyzeText(imageProxy)
        } else {
            analyzeBarcode(imageProxy)
        }
    }

    private fun analyzeBarcode(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                    if (value != null) {
                        val now = System.currentTimeMillis()
                        if (value != lastValue || now - lastAt > 2000) {
                            lastValue = value
                            lastAt = now
                            onIsbn(value)
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } catch (t: Throwable) {
            runCatching { imageProxy.close() }
        }
    }

    private fun analyzeText(imageProxy: ImageProxy) {
        // OCR agirdir: siradaki kareler islenirken yeni kare aciga alinmaz.
        if (busy) {
            imageProxy.close()
            return
        }
        busy = true
        try {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                busy = false
                imageProxy.close()
                return
            }
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            textRecognizer.process(image)
                .addOnSuccessListener { text -> onOcrLines(extractOcrLines(text)) }
                .addOnCompleteListener {
                    busy = false
                    imageProxy.close()
                }
        } catch (t: Throwable) {
            busy = false
            runCatching { imageProxy.close() }
        }
    }

    fun close() {
        scanner.close()
        textRecognizer.close()
    }
}

private fun extractOcrLines(text: OcrText): List<OcrLine> {
    val out = ArrayList<OcrLine>()
    for (block in text.textBlocks) {
        for (line in block.lines) {
            val t = line.text.trim()
            if (t.length < 2) continue
            out += OcrLine(t, line.boundingBox?.height() ?: 0)
        }
    }
    return out
}

/**
 * Kitap adi adayi: kapakta en buyuk puntolu yazi genelde addir.
 * Boyca birbirine yakin buyuk satirlar adin devamiysa (en fazla 2 satir) birlestirilir.
 */
private fun pickTitle(lines: List<OcrLine>): String {
    val usable = lines.filter {
        it.text.any { c -> c.isLetter() } && it.text.length in 2..90
    }
    if (usable.isEmpty()) return ""
    val maxH = usable.maxOf { it.height }.toFloat().coerceAtLeast(1f)
    val big = usable.filter { it.height >= maxH * 0.8f }
    return big.take(2).joinToString(" ") { it.text }.trim()
}

/**
 * Yazar adayi: "YAZAN/ÇEVİREN" isaretli satirlar ya da ad benzeri (2-4 kelimelik)
 * satirlar. Bulunamazsa bos doner; kullanici elle doldurabilir.
 */
private fun pickAuthor(lines: List<OcrLine>, title: String): String {
    val nameRe = Regex("^[A-ZÇĞİÖŞÜ][\\p{L}.'-]+(\\s+[A-ZÇĞİÖŞÜ][\\p{L}.'-]+){1,3}$")
    val markerRe = Regex("(?i)^(yazan|yazarı|yazar|çeviren|hazırlayan)\\s*[:\\-]?\\s*")
    for (l in lines) {
        val t = l.text.trim()
        if (t.equals(title, ignoreCase = true)) continue
        val candidate = markerRe.replace(t, "").trim().ifBlank { t }
        val words = candidate.split(Regex("\\s+"))
        if (words.size in 2..4 && candidate.matches(nameRe)) {
            return candidate
        }
    }
    return ""
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFFD9A05B) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
        color = if (selected) Color(0xFF201408) else Color(0xCCF3E9DA),
        style = MaterialTheme.typography.labelLarge
    )
}

/** Kapak adi modu: OCR kutulari + Ara. */
@Composable
private fun CoverSearchPanel(
    title: String,
    author: String,
    onTitle: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onRescan: () -> Unit,
    onSearch: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xE6120E0B))
            .padding(16.dp)
    ) {
        Text(
            text = "Kapaktan okunan bilgiler",
            color = Color(0x99F3E9DA),
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(10.dp))
        OcrField(title, "Kitap adı", onTitle)
        Spacer(Modifier.height(10.dp))
        OcrField(author, "Yazar (varsa)", onAuthor)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onRescan,
                border = BorderStroke(1.dp, Color(0x66F3E9DA)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF3E9DA))
            ) { Text("Yeniden oku") }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onSearch,
                enabled = title.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD9A05B),
                    contentColor = Color(0xFF201408)
                )
            ) { Text("Ara") }
        }
    }
}

@Composable
private fun OcrField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(color = Color(0xFFF3E9DA)),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFFF3E9DA),
            unfocusedTextColor = Color(0xFFF3E9DA),
            cursorColor = Color(0xFFD9A05B),
            focusedBorderColor = Color(0xFFD9A05B),
            unfocusedBorderColor = Color(0x66F3E9DA),
            focusedLabelColor = Color(0xFFD9A05B),
            unfocusedLabelColor = Color(0x99F3E9DA),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}
