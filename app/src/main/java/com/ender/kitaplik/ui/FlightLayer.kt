package com.ender.kitaplik.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ender.kitaplik.ui.shelf.BookSpine
import com.ender.kitaplik.ui.shelf.ShelfBook
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sirt -> kapak donusumu: sirt raftan yukari suzulur, buyur ve tam ekran kapağa oturur.
 *
 * Not: 3D dondurme (rotationY + kucuk cameraDistance) ve shadowElevation bazi GPU'larda
 * kareyi saniyelerce kilitliyordu (olctuk: 4950 ms GPU). Bu yuzden donus tamamen 2D:
 * konum + olcek + alfa. Ayni "raftan cikip ekrana gelme" hissi korunur, kare dusmez.
 *
 * Tek bir Animatable (0f -> 1f) tum zinciri surer.
 */
@Composable
fun FlightLayer(
    book: ShelfBook,
    start: Rect,
    target: Rect,
    progress: Animatable<Float, AnimationVector1D>
) {
    val density = LocalDensity.current
    val entity = book.entity

    Box(Modifier.fillMaxSize()) {
        // arka plan karartma: sirt raftan cikarken raf hala gorunur kalsin diye
        // gec baslar. Boylece "kitap raftan cikti" ani okunabiliyor.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = smooth(0.20f, 0.62f, progress.value) }
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0B0A09), Color(0xFF1B1512)))
                )
        )

        // 1) sirtin yerinden cikip hedefe oturan kapak (altta)
        if (entity != null) {
            BookCover(
                book = entity,
                modifier = Modifier
                    .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                    .size(
                        width = with(density) { target.width.toDp() },
                        height = with(density) { target.height.toDp() }
                    )
                    .graphicsLayer {
                        val p = progress.value
                        // Sirt once kalkar; kapak ancak onun ardindan belirir.
                        alpha = smooth(0.16f, 0.44f, p)

                        val t = smooth(0.22f, 0.94f, p)
                        val sx = (start.width / target.width).coerceIn(0.06f, 1f)
                        val sy = (start.height / target.height).coerceIn(0.06f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        scaleX = sx + (1f - sx) * t
                        scaleY = sy + (1f - sy) * t
                        translationX = (start.center.x - target.center.x) * (1f - t)
                        // hafif yay: ortada biraz yukari suzulur, sonra yerine oturur
                        val lift = sin(t * Math.PI.toFloat()) * 26.dp.toPx()
                        translationY = (start.center.y - target.center.y) * (1f - t) - lift
                    },
                corner = 18.dp,
                subcompose = false
            )
        }

        // 2) raftan cikan sirt (ustte): once yukari suzulur, sonra kapağa donusur
        if (start.width > 1f && start.height > 1f) {
            Box(
                Modifier
                    .offset { IntOffset(start.left.roundToInt(), start.top.roundToInt()) }
                    .graphicsLayer {
                        val p = progress.value
                        val out = smooth(0.06f, 0.42f, p)
                        alpha = 1f - out
                        transformOrigin = TransformOrigin(0.5f, 0.9f)
                        translationY = -26.dp.toPx() * out
                        scaleX = 1f + 0.14f * out
                        scaleY = 1f + 0.14f * out
                    }
            ) {
                BookSpine(
                    book = book,
                    onClick = {},
                    onBounds = {},
                    heightDpOverride = with(density) { start.height.toDp() }
                )
            }
        }
    }
}

/** 0..1 araliginda yumusak gecis (smoothstep). */
private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
