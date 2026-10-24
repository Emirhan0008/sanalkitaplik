package com.ender.kitaplik.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Tam ekran barkod tarayici. Ilk okunan ISBN'i iletir.
 */
@Composable
fun ScannerOverlay(
    onClose: () -> Unit,
    onManualEntry: () -> Unit,
    onIsbn: (String) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }

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
            CameraScannerPreview(onIsbn = { isbn ->
                // Barkod okundugunda kisa tik: tarama basarisini elle hissettirir.
                context.tickHaptic(26)
                onIsbn(isbn)
            })
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
        ScanChrome()

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
private fun ScanChrome() {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val frameWidth = size.width * 0.78f
            val frameHeight = frameWidth * 0.52f
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
private fun CameraScannerPreview(onIsbn: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val released = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val currentOnIsbn by rememberUpdatedState(onIsbn)
    val analyzer = remember { BarcodeAnalyzer { value -> currentOnIsbn(value) } }

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
                        val analysis = ImageAnalysis.Builder()
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

private class BarcodeAnalyzer(
    private val onIsbn: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
            .build()
    )

    @Volatile
    private var lastValue: String? = null

    @Volatile
    private var lastAt: Long = 0L

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
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

    fun close() {
        scanner.close()
    }
}
