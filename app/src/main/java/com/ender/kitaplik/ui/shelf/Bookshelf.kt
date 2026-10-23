package com.ender.kitaplik.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// ---- vintage ceviz paleti ----
private val ShelfBack = Color(0xFFE7D6B6)
private val ShelfBackDeep = Color(0xFFD8C3A0)
private val FrameDark = Color(0xFF35210F)
private val FrameDeep = Color(0xFF43290F)
private val FrameMid = Color(0xFF5C3B1D)
private val FrameLight = Color(0xFF7A5230)
private val FrameHighlight = Color(0xFF96703F)
private val PlankLight = Color(0xFF8A6438)
private val PlankMid = Color(0xFF6B4A26)
private val PlankDeep = Color(0xFF48301A)
private val GoldBand = Color(0xFFC9A24A)
private val GoldBright = Color(0xFFE8CD86)
private val GoldDeep = Color(0xFF8E6C24)

/** Doygun, eskitilmis deri cilt renkleri. */
private val SpineColors = listOf(
    0xFF8B2E2E, 0xFF2F5B3E, 0xFF2C3E6B, 0xFFB07B24, 0xFF6B1F2A,
    0xFF24615C, 0xFF5B2F4E, 0xFFA34A22, 0xFF5E5A24, 0xFF6B4326,
    0xFF3A4E63, 0xFF7A3A2A, 0xFF44455C, 0xFF8A5A1E, 0xFF4E3A2A,
    0xFF74303C, 0xFF31544A, 0xFF5A4A2E, 0xFF3F3A55, 0xFF8C4A2E
)

private val LabelColors = listOf(
    0xFFD9BE7E, 0xFFE7D6B0, 0xFFC9A24A, 0xFFDCC596, 0xFFB99B5E
)

/**
 * Vintage kitaplik: kaydirmasiz, tum satirlar tek ekranda.
 * Oymali ceviz kasa (ust tac, yan stil, alt baza) + sikisik sirtlar.
 */
@Composable
fun Bookshelf(
    rows: List<ShelfRow>,
    interactive: Boolean,
    onRealBookClick: (ShelfBook) -> Unit,
    onBounds: (ShelfBook, Rect) -> Unit,
    modifier: Modifier = Modifier,
    /** Arama sirasinda eslesen sirtlarin anahtarlari; bos -> arama kapali. */
    matchedKeys: Set<String> = emptySet(),
    searching: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(3.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(FrameDark, FrameMid, FrameLight, FrameMid, FrameDark)
                )
            )
    ) {
        WoodGrain(Modifier.fillMaxSize(), vertical = true, alpha = 0.15f)
        SideStiles(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
            Crown(Modifier.fillMaxWidth())

            Column(Modifier.fillMaxWidth().weight(1f)) {
                rows.forEach { row ->
                    ShelfRowView(
                        row = row,
                        interactive = interactive,
                        onRealBookClick = onRealBookClick,
                        onBounds = onBounds,
                        matchedKeys = matchedKeys,
                        searching = searching,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Plinth(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Crown(modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(Brush.verticalGradient(listOf(FrameHighlight, FrameLight)))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(15.dp)
                .background(Brush.verticalGradient(listOf(FrameLight, FrameMid)))
        ) {
            BeadBand(Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.verticalGradient(listOf(FrameDark, Color(0xFF24140A))))
        )
    }
}

@Composable
private fun Plinth(modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF24140A), FrameDark)))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(Brush.verticalGradient(listOf(FrameMid, FrameLight, FrameMid)))
        ) {
            BeadBand(Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Brush.verticalGradient(listOf(FrameLight, FrameDark)))
        )
    }
}

/** Oyma boncuk/damla dizisi. */
@Composable
private fun BeadBand(modifier: Modifier) {
    Canvas(modifier) {
        val step = 26.dp.toPx()
        val radius = 3.4.dp.toPx()
        val cy = size.height / 2f
        var x = step / 2f
        while (x < size.width) {
            val diamond = Path().apply {
                moveTo(x, cy - radius)
                lineTo(x + radius, cy)
                lineTo(x, cy + radius)
                lineTo(x - radius, cy)
                close()
            }
            drawPath(diamond, GoldDeep)
            val inner = Path().apply {
                moveTo(x, cy - radius * 0.55f)
                lineTo(x + radius * 0.55f, cy)
                lineTo(x, cy + radius * 0.55f)
                lineTo(x - radius * 0.55f, cy)
                close()
            }
            drawPath(inner, GoldBand)
            x += step
        }
    }
}

/** Yan stillerdeki oyma kanallar. */
@Composable
private fun SideStiles(modifier: Modifier) {
    Canvas(modifier) {
        val dark = Color(0xFF231407).copy(alpha = 0.55f)
        val light = Color(0xFFA57B48).copy(alpha = 0.45f)
        val groove = 4.dp.toPx()
        var x = 3.dp.toPx()
        repeat(2) {
            drawLine(dark, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5f)
            drawLine(light, Offset(x + 1.6f, 0f), Offset(x + 1.6f, size.height), strokeWidth = 1.2f)
            x += groove
        }
        var rx = size.width - 3.dp.toPx()
        repeat(2) {
            drawLine(dark, Offset(rx, 0f), Offset(rx, size.height), strokeWidth = 2.5f)
            drawLine(light, Offset(rx - 1.6f, 0f), Offset(rx - 1.6f, size.height), strokeWidth = 1.2f)
            rx -= groove
        }
        // altin ince cizgi
        drawLine(
            GoldBand.copy(alpha = 0.35f),
            Offset(11.dp.toPx(), 0f),
            Offset(11.dp.toPx(), size.height),
            strokeWidth = 1f
        )
        drawLine(
            GoldBand.copy(alpha = 0.35f),
            Offset(size.width - 11.dp.toPx(), 0f),
            Offset(size.width - 11.dp.toPx(), size.height),
            strokeWidth = 1f
        )
    }
}

@Composable
private fun WoodGrain(modifier: Modifier, vertical: Boolean, alpha: Float) {
    Canvas(modifier) {
        val step = 7f
        val color = Color(0xFF1F1106).copy(alpha = alpha)
        if (vertical) {
            var x = 0f
            while (x < size.width) {
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
        } else {
            var y = 0f
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
    }
}

@Composable
private fun ShelfRowView(
    row: ShelfRow,
    interactive: Boolean,
    onRealBookClick: (ShelfBook) -> Unit,
    onBounds: (ShelfBook, Rect) -> Unit,
    matchedKeys: Set<String>,
    searching: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val contentHeight = maxHeight
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(ShelfBack, ShelfBackDeep),
                            startY = 0f,
                            endY = 700f
                        )
                    )
                    .clipToBounds()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(row.spacingDp.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    row.books.forEach { book ->
                        val factor = (book.heightDp / row.heightDp).coerceIn(0.86f, 1f)
                        val matched = searching && book.key in matchedKeys
                        BookSpine(
                            book = book,
                            onClick = { if (book.isReal) onRealBookClick(book) },
                            onBounds = { rect -> if (book.isReal) onBounds(book, rect) },
                            heightDpOverride = contentHeight * factor,
                            interactive = interactive,
                            fillWidth = true,
                            matched = matched,
                            dimmed = searching && !matched,
                            modifier = Modifier.weight(book.widthDp.coerceAtLeast(2f))
                        )
                    }
                }

                // rafin ic golgesi (kitap arkalarinda)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x2E21140A), Color(0x0821140A), Color(0x1F21140A))
                            )
                        )
                )
            }
        }

        // raf tahtasi (profilli)
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(Brush.verticalGradient(listOf(PlankLight, PlankMid, PlankDeep, PlankMid)))
        ) {
            WoodGrain(Modifier.fillMaxSize(), vertical = false, alpha = 0.24f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0x88E8CFA4))
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .align(Alignment.BottomCenter)
                    .background(GoldDeep.copy(alpha = 0.45f))
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(Brush.verticalGradient(listOf(Color(0x40211409), Color(0x00000000))))
        )
    }
}

/**
 * Sirt rengine gore okunur yazi rengi: koyu ciltte krem, acik ciltte koyu kahve.
 * Boylece butun sirtlar ayni beyazla bogulmaz; her cilt kendi kontrastini alir.
 */
private fun spineInk(base: Color, real: Boolean): Color {
    fun lin(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    val lum = 0.2126f * lin(base.red) + 0.7152f * lin(base.green) + 0.0722f * lin(base.blue)
    val lightInk = Color(0xFFF9EFDA)
    val darkInk = Color(0xFF2A1A0F)
    return if (lum < 0.28f) {
        lightInk.copy(alpha = if (real) 0.96f else 0.64f)
    } else {
        darkInk.copy(alpha = if (real) 0.92f else 0.58f)
    }
}

@Composable
fun BookSpine(
    book: ShelfBook,
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    heightDpOverride: Dp? = null,
    interactive: Boolean = true,
    fillWidth: Boolean = false,
    /** Arama eslesmesi: altin cerceve. */
    matched: Boolean = false,
    /** Arama sirasinda eslesmeyen sirtlar geri plana cekilir. */
    dimmed: Boolean = false
) {
    val seed = abs(book.colorSeed) % SpineColors.size
    val base = Color(SpineColors[seed])
    val labelColor = Color(LabelColors[(seed / 7) % LabelColors.size])
    val shade = base.copy(alpha = 0.38f).compositeOver(Color.Black)
    val light = base.copy(alpha = 0.18f).compositeOver(Color(0xFFE8DCC0))
    val spineHeight = heightDpOverride ?: book.heightDp.dp
    val tilt = if (book.isReal) 0f else book.tiltDeg
    val budget = if (book.isReal) 0f else book.tiltBudgetDp.coerceAtLeast(0f)

    Box(
        modifier = modifier
            .then(
                if (fillWidth) Modifier.fillMaxWidth()
                else Modifier.width(book.widthDp.dp)
            )
            .height(spineHeight)
            .padding(
                start = if (tilt < 0f) budget.dp else 0.dp,
                end = if (tilt > 0f) budget.dp else 0.dp
            )
            .graphicsLayer {
                rotationZ = tilt
                transformOrigin = TransformOrigin(0.5f, 1f)
                if (matched) {
                    scaleX = 1.05f
                    scaleY = 1.05f
                }
            }
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .background(base)
            .drawWithContent {
                drawContent()
                if (dimmed) {
                    // Eslesmeyen sirtlar karartilir (rafin arkasina gomulur).
                    drawRect(color = Color(0x8C211409))
                }
                if (matched) {
                    drawRect(color = GoldBright, style = Stroke(width = 5f))
                }
            }
            .then(if (book.isReal && interactive) Modifier.clickable { onClick() } else Modifier)
    ) {
        // cilt yuzeyi: ust isik, alt golge
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x24FFFFFF),
                        0.18f to Color(0x00FFFFFF),
                        0.82f to Color(0x12000000),
                        1f to Color(0x3D000000)
                    )
                )
        )
        // yan bombeleri
        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .align(Alignment.CenterStart)
                .background(shade)
        )
        Box(
            Modifier
                .fillMaxHeight()
                .width(1.dp)
                .align(Alignment.CenterEnd)
                .background(light)
        )

        val decoType = (seed / 5) % 5
        val labelHeight = (book.heightDp * 0.11f).dp

        // ust/alt altin seritler (her ciltte)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 7.dp)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(GoldBand.copy(alpha = 0.55f))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 7.dp)
                .fillMaxWidth()
                .height(1.5.dp)
                .background(GoldBand.copy(alpha = 0.55f))
        )

        when (decoType) {
            0 -> {
                // cift etiket
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .fillMaxWidth(0.52f)
                        .height(labelHeight)
                        .background(labelColor.copy(alpha = 0.85f), RoundedCornerShape(1.dp))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .fillMaxWidth(0.42f)
                        .height(labelHeight * 0.7f)
                        .background(labelColor.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                )
            }
            1 -> {
                // orta rozet + ince cizgiler
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 9.dp, height = 9.dp)
                        .graphicsLayer { rotationZ = 45f }
                        .background(GoldBand.copy(alpha = 0.8f))
                )
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 22.dp)
                        .fillMaxWidth(0.45f)
                        .height(1.5.dp)
                        .background(GoldBand.copy(alpha = 0.6f))
                )
            }
            2 -> {
                // uzun etiket
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 22.dp)
                        .fillMaxWidth(0.62f)
                        .height(labelHeight * 1.4f)
                        .background(labelColor.copy(alpha = 0.82f), RoundedCornerShape(1.dp))
                )
            }
            3 -> {
                // uc ince serit
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .height(1.2.dp)
                        .background(GoldBand.copy(alpha = 0.45f))
                )
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(1.2.dp)
                        .background(GoldBand.copy(alpha = 0.45f))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .fillMaxWidth()
                        .height(1.2.dp)
                        .background(GoldBand.copy(alpha = 0.45f))
                )
            }
            else -> {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .fillMaxWidth(0.46f)
                        .height(labelHeight * 0.9f)
                        .background(labelColor.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(0.3f)
                        .height(1.5.dp)
                        .background(GoldBand.copy(alpha = 0.7f))
                )
            }
        }

        // sirt yazisi: cok kucuk baslik + yazar (Canvas uzerinde, 180 dugum olusturmadan)
        run {
            val spineTitle = book.title.trim()
            if (spineTitle.isNotBlank()) {
                val author = book.authors.substringBefore(',').trim()
                val label = if (author.isBlank()) spineTitle else "$spineTitle · $author"
                val ink = spineInk(base, book.isReal)
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer(cacheSize = 32)
                val style = TextStyle(
                    color = ink,
                    fontSize = if (book.isReal) 6.sp else 5.sp,
                    fontFamily = FontFamily.Serif
                )
                // sirt yuksekliginin neredeyse tamami yazi icin kullanilir; kenarda 8dp pay
                val maxWidthPx = with(density) { (spineHeight - 16.dp).roundToPx() }.coerceAtLeast(1)
                val layout = remember(label, book.isReal, maxWidthPx, density) {
                    measurer.measure(
                        text = label,
                        style = style,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = maxWidthPx)
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    rotate(degrees = -90f, pivot = center) {
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = center.x - layout.size.width / 2f,
                                y = center.y - layout.size.height / 2f
                            )
                        )
                    }
                }
            }
        }

        if (book.isReal) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(GoldBright)
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(3.dp)
                    .background(GoldBright, RoundedCornerShape(50))
            )
        }
    }
}
