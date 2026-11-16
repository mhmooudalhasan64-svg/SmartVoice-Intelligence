package com.aurevon.velora.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aurevon.velora.recordings.RecordingItem
import com.aurevon.velora.ui.theme.Accent
import com.aurevon.velora.ui.theme.Muted
import com.aurevon.velora.ui.theme.Panel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecordingRow(
    item: RecordingItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(item.file.lastModified()) {
        SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale.getDefault())
            .format(Date(item.file.lastModified()))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(date, color = Color.White, fontWeight = FontWeight.Medium)
                val durationLabel = item.durationSeconds?.let { "${it / 60}:%02d".format(it % 60) } ?: "المدة غير متاحة"
                Text("$durationLabel • %.1f MB".format(item.file.length() / (1024.0 * 1024.0)),
                    color = Muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = if (isPlaying) "يعمل" else "تشغيل",
                    tint = Accent)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف التسجيل",
                    tint = Color(0xFFFF9292))
            }
        }
    }
}

@Composable
internal fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp),
                color = Accent.copy(alpha = 0.12f)) {
                Icon(icon, null, tint = Accent, modifier = Modifier.padding(9.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(description, color = Muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}
