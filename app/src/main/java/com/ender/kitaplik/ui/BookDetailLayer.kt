package com.ender.kitaplik.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ender.kitaplik.data.BookEntity
import kotlin.math.roundToInt

/**
 * Ucus bittikten sonra acilan tam ekran detay.
 * Kapak, ucusun bittigi dikdortgenin tam uzerine oturur -> dikissiz gecis.
 */
@Composable
fun BookDetailLayer(
    book: BookEntity,
    target: Rect,
    visible: Boolean,
    onClose: () -> Unit,
    onRemove: () -> Unit,
    onRetryEnrich: () -> Unit
) {
    var confirmRemove by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 260 else 120),
        label = "detailContentAlpha"
    )
    val backdrop by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(260),
        label = "detailBackdrop"
    )
    // Kapak, ucus tamamlandiginda ANINDA gorunur (yer degistirmeden dikissiz gecis),
    // kapanirken ise hizla silinir ki ucus animasyonu gorunur olsun.
    val coverAlpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 0 else 80),
        label = "detailCoverAlpha"
    )

    val infoTopPx = target.bottom + with(density) { 24.dp.toPx() }
    val infoMaxHeightDp = with(density) {
        (screenHeightPx - infoTopPx - with(density) { 28.dp.toPx() }.toFloat()).toDp()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backdrop }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFBF3), Color(0xFFF6E9D7), Color(0xFFF0E1CC))
                    )
                )
        )

        // kapagin arkasindaki sicak isik
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (target.left - 90f).roundToInt(),
                        (target.top - 60f).roundToInt()
                    )
                }
                .size(
                    width = with(density) { (target.width + 180f).toDp() },
                    height = with(density) { (target.height + 180f).toDp() }
                )
                .graphicsLayer { alpha = backdrop * 0.55f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33C08457), Color.Transparent),
                        center = Offset.Unspecified
                    ),
                    RoundedCornerShape(50)
                )
        )

        val coverAlphaNow = coverAlpha.value
        BookCover(
            book = book,
            modifier = Modifier
                .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
                .size(
                    width = with(density) { target.width.toDp() },
                    height = with(density) { target.height.toDp() }
                )
                .graphicsLayer { alpha = coverAlphaNow },
            corner = 18.dp
        )

        Column(
            Modifier
                .offset { IntOffset(0, infoTopPx.roundToInt()) }
                .fillMaxWidth()
                .heightIn(min = 0.dp, max = infoMaxHeightDp.coerceAtLeast(80.dp))
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 30.dp)
                .graphicsLayer { alpha = contentAlpha }
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (book.authors.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = book.authors,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(Modifier.height(18.dp))
            LibraryBadge()
            if (!book.enriched && book.enrichAttempts >= 5) {
                Spacer(Modifier.height(10.dp))
                MissingMetaBadge(onRetryEnrich)
            }
            Spacer(Modifier.height(18.dp))

            MetaRow("Yayınevi", book.publisher)
            MetaRow("Basım", book.publishedDate)
            MetaRow("Sayfa", if (book.pageCount > 0) "${book.pageCount}" else "")
            MetaRow("ISBN", book.isbn)

            if (book.description.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = book.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = { confirmRemove = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = BorderStroke(1.dp, Color(0x336B4A2F))
            ) {
                Text("Kitaplıktan çıkar", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(40.dp))
        }

        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("Kitaplıktan çıkarılsın mı?") },
                text = {
                    Text("\"${book.title}\" raftan kaldırılacak. Birkaç saniye içinde geri alabilirsin.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmRemove = false
                            onRemove()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Çıkar") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemove = false }) { Text("Vazgeç") }
                },
                containerColor = Color(0xFFFFFBF3),
                titleContentColor = Color(0xFF3A2E24),
                textContentColor = Color(0xFF5A4B3C)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(10.dp)
                .size(44.dp)
                .graphicsLayer { alpha = contentAlpha }
                .clip(RoundedCornerShape(50))
                .background(Color(0x14000000))
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Kapat",
                tint = Color(0xFF3A2E24)
            )
        }
    }
}

@Composable
private fun LibraryBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x1FB4683C))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "KÜTÜPHANENDE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MissingMetaBadge(onRetry: () -> Unit) {
    var queued by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x1FC0392B))
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Künye eksik",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8E3B2C),
            fontSize = 10.sp
        )
        if (queued) {
            Text(
                "sıraya alındı",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color(0xFF8E3B2C),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        } else {
            TextButton(
                onClick = {
                    queued = true
                    onRetry()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    "yeniden dene",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = Color(0xFF8E3B2C)
                )
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
