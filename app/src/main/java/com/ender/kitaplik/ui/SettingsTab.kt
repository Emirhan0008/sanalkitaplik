package com.ender.kitaplik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ender.kitaplik.data.BookMetadataSource

/**
 * Ayarlar sekmesi: kaynak sagligi (Google Books devre kesici + son hata),
 * bakim islemleri (onbellegi temizle, yedekleme) ve surum bilgisi.
 */
@Composable
fun SettingsTab(
    health: BookMetadataSource.SourceHealth,
    apiKeyConfigured: Boolean,
    versionLabel: String,
    onClearCache: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val googleDown = health.googleDownUntil > now
    val minutesLeft = ((health.googleDownUntil - now) / 60_000L).toInt().coerceAtLeast(1)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Ayarlar",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Kaynaklar, bakım ve yedekleme",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { SectionLabel("KAYNAKLAR") }

        item {
            StatusRow(
                icon = Icons.Rounded.Cloud,
                title = "Google Books",
                status = if (googleDown) "devre dışı · $minutesLeft dk" else "aktif",
                statusActive = !googleDown,
                subtitle = when {
                    googleDown && health.googleLastError != null -> "son hata: ${health.googleLastError}"
                    googleDown -> "10 dk bekleme sürüyor"
                    else -> "birincil kaynak"
                }
            )
        }

        item {
            StatusRow(
                icon = Icons.Rounded.Cloud,
                title = "Open Library",
                status = if (googleDown) "kullanılıyor" else "yedek",
                statusActive = true,
                subtitle = if (googleDown) {
                    "Google kapalıyken künye buradan geliyor"
                } else {
                    "Google sonuç vermezse devreye girer"
                }
            )
        }

        // Anahtar satiri yalnizca anahtar tanimliysa gosterilir.
        if (apiKeyConfigured) {
            item {
                StatusRow(
                    icon = Icons.Rounded.Key,
                    title = "Google Books API anahtarı",
                    status = "ayarlı",
                    statusActive = true,
                    subtitle = "BuildConfig.GBOOKS_KEY ile derlendi"
                )
            }
        }

        item { SectionLabel("BAKIM") }

        item {
            ActionRow(
                icon = Icons.Rounded.Cached,
                title = "Önbelleği temizle",
                subtitle = "Kapak görsellerini siler; yenileri zamanla yeniden indirilir",
                onClick = onClearCache
            )
        }

        item {
            ActionRow(
                icon = Icons.Rounded.Save,
                title = "Yedek al (.json)",
                subtitle = "Kütüphaneni dosya olarak kaydet",
                onClick = onExport
            )
        }

        item {
            ActionRow(
                icon = Icons.Rounded.Restore,
                title = "Yedekten yükle",
                subtitle = "JSON yedeğini içe aktar (var olan kitaplar atlanır)",
                onClick = onImport
            )
        }

        item { SectionLabel("UYGULAMA") }

        item {
            InfoRow(title = "Sürüm", value = versionLabel)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF8A6A4B),
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    title: String,
    status: String,
    statusActive: Boolean,
    subtitle: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF8A6A4B),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        StatusChip(status, active = statusActive)
    }
}

@Composable
private fun StatusChip(text: String, active: Boolean) {
    val bg = if (active) Color(0x1F3E6B3A) else Color(0x1FA65A2A)
    val fg = if (active) Color(0xFF3E6B3A) else Color(0xFFA65A2A)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF8A6A4B),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}