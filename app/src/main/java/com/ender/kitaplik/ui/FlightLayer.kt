package com.ender.kitaplik.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * Sirt -> kapak donusumu.
 *
 * 0.00 - 0.55 : sirt raftan disari cikar, kendi ekseninde doner, kararir
 * 0.32 - 1.00 : kapak buyuyerek tam ekran olcusune oturur
 *
 * Tek bir Animatable (0f -> 1f) tum zinciri surer; boylece animasyon
 * kesintiye ugrasa bile her zaman tutarli bir karede kalir.
 */
@Composable
fun FlightLayer(
    book: ShelfBook,
    start: Rect,
    target: Rect,
    progress: Animatable<Float, AnimationVector1D>
) {
    val density = LocalDensity.current
    val d = density.density
    val entity = book.entity

    Box(Modifier.fillMaxSize()) {
        // arka plan karartma
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.94f * progress.value }
                .background(Brush.verticalGradient(listOf(Color(0xFF0B0A09), Color(0xFF1B1512))))
        )

        // 1) raftan cikan sirt
        if (start.width > 1f && start.height > 1f) {
            Box(
                Modifier
                    .offset { IntOffset(start.left.roundToInt(), start.top.roundToInt()) }
                    .graphicsLayer {
                        val appear = progress.value
                        val out = smooth(0.02f, 0.5f, appear)
                        alpha = 1f - out
                        transformOrigin = TransformOrigin(0.5f, 0.85f)
                        translationY = -90f * d * out
                        rotationY = -78f * out
                        scaleX = 1f + 0.22f * out
                        scaleY = 1f + 0.22f * out
                        cameraDistance = 22f * d
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

        // 2) kapağa donusen kitap
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
                        val appear = smooth(0.30f, 0.82f, p)
                        alpha = appear
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        val s = 0.70f + 0.30f * smooth(0.30f, 1f, p)
                        scaleX = s
                        scaleY = s
                        shadowElevation = 24f * d * appear
                        shape = RoundedCornerShape(18.dp)
                        clip = true
                    },
                corner = 18.dp
            )
        }
    }
}

private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
