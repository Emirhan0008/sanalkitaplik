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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// ---- vintage palet ----
private val ShelfBack = Color(0xFFEFE1C6)
private val ShelfBackDeep = Color(0xFFE2D0B0)
private val FrameDark = Color(0xFF4A301C)
private val FrameMid = Color(0xFF6B4A2F)
private val FrameLight = Color(0xFF8A6440)
private val FrameHighlight = Color(0xFFA07A52)
private val PlankLight = Color(0xFF7E5A38)
private val PlankMid = Color(0xFF664628)
private val PlankDeep = Color(0xFF4A3119)
private val GoldBand = Color(0xFFC8A24A)
private val GoldBright = Color(0xFFE3C77E)

/** Eskitilmis, soluk cilt renkleri. */
private val SpineColors = listOf(
    0xFF8E3B2F, 0xFF7A3B4A, 0xFF4A5A46, 0xFF3E4E5E, 0xFF8A6A32,
    0xFF5B4636, 0xFF6B3B2E, 0xFF45524A, 0xFF7A5A43, 0xFF3F3B52,
    0xFF94402F, 0xFF2F4A54, 0xFF6E5130, 0xFF553A2C, 0xFF7A4A52,
    0xFF3A4A3E, 0xFF8A5A34, 0xFF4E4230, 0xFF5E4A62, 0xFF6A4A3A
)

private val LabelColors = listOf(
    0xFFD9C08A, 0xFFE8D9B5, 0xFFC8A24A, 0xFFE5D6BC, 0xFFCDB588
)

/**
 * Vintage kitaplik: kaydirmasiz, tum satirlar tek ekranda.
 * Ahsap kasa + damar dokusu + sikisik dizilmis sirtlar.
 */
@Composable
fun Bookshelf(
    rows: List<ShelfRow>,
    interactive: Boolean,
    onRealBookClick: (ShelfBook) -> Unit,
    onBounds: (ShelfBook, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(FrameDark, FrameMid, FrameLight, FrameMid, FrameDark)
                )
            )
    ) {
        WoodGrain(Modifier.fillMaxSize(), vertical = true, alpha = 0.13f)

        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp)) {
            // ust kasa kenari
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Brush.verticalGradient(listOf(FrameHighlight, FrameLight, FrameMid)))
            ) {
                WoodGrain(Modifier.fillMaxSize(), vertical = false, alpha = 0.16f)
            }

            Column(Modifier.fillMaxWidth().weight(1f)) {
                rows.forEach { row ->
                    ShelfRowView(
                        row = row,
                        interactive = interactive,
                        onRealBookClick = onRealBookClick,
                        onBounds = onBounds,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // alt kasa kenari
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(Brush.verticalGradient(listOf(FrameMid, FrameLight, FrameDark)))
            ) {
                WoodGrain(Modifier.fillMaxSize(), vertical = false, alpha = 0.16f)
            }
        }
    }
}

@Composable
private fun WoodGrain(modifier: Modifier, vertical: Boolean, alpha: Float) {
    Canvas(modifier) {
        val step = 7f
        val color = Color(0xFF2B1B0F).copy(alpha = alpha)
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
                        BookSpine(
                            book = book,
                            onClick = { if (book.isReal) onRealBookClick(book) },
                            onBounds = { rect -> if (book.isReal) onBounds(book, rect) },
                            heightDpOverride = contentHeight * factor,
                            interactive = interactive
                        )
                    }
                }

                // rafin ic golgesi
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x1A21140A), Color.Transparent, Color(0x1421140A))
                            )
                        )
                )
            }
        }

        // raf tahtasi
        Box(
            Modifier
                .fillMaxWidth()
                .height(15.dp)
                .background(Brush.verticalGradient(listOf(PlankLight, PlankMid, PlankDeep)))
        ) {
            WoodGrain(Modifier.fillMaxSize(), vertical = false, alpha = 0.22f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0x66E8CFA4))
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(Brush.verticalGradient(listOf(Color(0x33211409), Color(0x00000000))))
        )
    }
}

@Composable
fun BookSpine(
    book: ShelfBook,
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    heightDpOverride: Dp? = null,
    interactive: Boolean = true
) {
    val seed = abs(book.colorSeed) % SpineColors.size
    val base = Color(SpineColors[seed])
    val labelColor = Color(LabelColors[(seed / 7) % LabelColors.size])
    val shade = base.copy(alpha = 0.34f).compositeOver(Color.Black)
    val light = base.copy(alpha = 0.16f).compositeOver(Color(0xFFE8DCC0))
    val spineHeight = heightDpOverride ?: book.heightDp.dp

    Box(
        modifier = modifier
            .width(book.widthDp.dp)
            .height(spineHeight)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .background(base)
            .then(if (book.isReal && interactive) Modifier.clickable { onClick() } else Modifier)
    ) {
        // yaslanmis cilt: kenarlar mat, ortada hafif eskitme
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x1FFFFFFF),
                        0.25f to Color(0x00FFFFFF),
                        0.85f to Color(0x14000000),
                        1f to Color(0x33000000)
                    )
                )
        )
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

        val decoType = (seed / 13) % 4
        when (decoType) {
            0 -> {
                GoldLine(top = 12.dp)
                GoldLine(bottom = 12.dp)
            }
            1 -> {
                LabelBlock(alignTop = true, top = 16.dp, widthFraction = 0.5f, heightFraction = 0.11f, color = labelColor)
                GoldLine(bottom = 14.dp)
            }
            2 -> {
                LabelBlock(alignTop = false, bottom = 20.dp, widthFraction = 0.55f, heightFraction = 0.15f, color = labelColor)
                GoldLine(top = 13.dp)
            }
            else -> {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(GoldBand.copy(alpha = 0.45f))
                )
                LabelBlock(alignTop = true, top = 12.dp, widthFraction = 0.42f, heightFraction = 0.08f, color = labelColor)
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

@Composable
private fun GoldLine(top: Dp = 0.dp, bottom: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.5.dp)
            .background(GoldBand.copy(alpha = 0.75f))
    )
}

@Composable
private fun LabelBlock(
    alignTop: Boolean,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    widthFraction: Float,
    heightFraction: Float,
    color: Color
) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(0.dp)
            .background(Color.Transparent)
    )
}

private fun Modifier.unused() = this
