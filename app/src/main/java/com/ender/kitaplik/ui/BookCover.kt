package com.ender.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.ender.kitaplik.data.BookEntity
import java.io.File
import kotlin.math.abs

private val Palettes = listOf(
    Color(0xFF7A4B3A) to Color(0xFF2C1A14),
    Color(0xFF3F5B63) to Color(0xFF16242A),
    Color(0xFF7D6A3F) to Color(0xFF2B2415),
    Color(0xFF4A3D5C) to Color(0xFF1E1828),
    Color(0xFF6B2F35) to Color(0xFF271114),
    Color(0xFF35544A) to Color(0xFF12211C),
    Color(0xFF8A6B4F) to Color(0xFF2A2019),
    Color(0xFF3C4A6B) to Color(0xFF151C2B)
)

/** Raf paletiyle ayni aileden, kapak icin doygun renkler. */
private val CoverPalettes = listOf(
    0xFFC2453F, 0xFF2F6B8A, 0xFFB4762E, 0xFF5E4A8C, 0xFF8E3448,
    0xFF32705F, 0xFF9A5A33, 0xFF3E5E8C, 0xFFA8542C, 0xFF4A4A6B,
    0xFFB4506E, 0xFF2E6E6A
)

/**
 * Kapak gorseli; kapak yoksa ISBN'den turetilen uretilmis kapak gosterir.
 * Raf karti, ucustaki katman ve detay ekrani ayni bileseni kullanir ki gecis kusursuz olsun.
 */
@Composable
fun BookCover(
    book: BookEntity,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    compact: Boolean = false,
    /** Animasyon icinde true: subcomposition her karede ek is yaptigi icin kapali tutulur. */
    subcompose: Boolean = true
) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(Color(0xFF241E19))) {
        val coverPath = book.coverPath
        val fileExists = remember(coverPath) {
            coverPath != null && File(coverPath).exists()
        }
        if (coverPath != null && fileExists) {
            val request = ImageRequest.Builder(LocalContext.current)
                .data(File(coverPath))
                .crossfade(false)
                .build()
            if (subcompose) {
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { GeneratedCover(book, Modifier.fillMaxSize(), compact) },
                    error = { GeneratedCover(book, Modifier.fillMaxSize(), compact) }
                )
            } else {
                // Animasyon ici: subcomposition yok. Uretimis kapak YALNIZCA gercek
                // kapak yuklenene kadar cizilir; sonraki karelerde hicbir is yapmaz.
                // (Her karede cizilirse metin olcusu + gradyan ucusu yavaslatiyordu.)
                var coverLoaded by remember(coverPath) { mutableStateOf(false) }
                if (!coverLoaded) {
                    GeneratedCover(book, Modifier.fillMaxSize(), compact)
                }
                AsyncImage(
                    model = request,
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { coverLoaded = true }
                )
            }
        } else {
            GeneratedCover(book, Modifier.fillMaxSize(), compact)
        }

        // cam parlamasi
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.12f))
                    )
                )
        )
    }
}

@Composable
private fun GeneratedCover(book: BookEntity, modifier: Modifier, compact: Boolean = false) {
    val seed = book.isbn.hashCode() and 0x7fffffff
    val palette = CoverPalettes[seed % CoverPalettes.size]
    val base = Color(palette)
    val deep = base.copy(alpha = 0.45f).compositeOver(Color.Black)

    if (compact) {
        Box(modifier.background(base)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(deep)
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .border(0.75.dp, Color(0x55F6E7CD), RoundedCornerShape(1.dp))
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(10.dp)
                    .height(24.dp)
                    .background(Color(0x40F6E7CD), RoundedCornerShape(1.dp))
            )
        }
        return
    }

    Box(modifier.background(base)) {
        // sol sirt golgesi
        Box(
            Modifier
                .fillMaxHeight()
                .width(9.dp)
                .background(deep)
        )
        // ince altin cerceve
        Box(
            Modifier
                .fillMaxSize()
                .padding(11.dp)
                .border(1.dp, Color(0x66F6E7CD), RoundedCornerShape(2.dp))
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 16.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp, lineHeight = 26.sp),
                color = Color(0xFFFDF5E7),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .width(26.dp)
                    .height(1.5.dp)
                    .background(Color(0xB3F6E7CD))
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = book.authors,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color(0xCCFDF5E7),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        // yayinevi isareti
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .width(14.dp)
                .height(14.dp)
                .background(Color(0x59F6E7CD), RoundedCornerShape(1.dp))
        )
    }
}
