package com.animeshadow.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.animeshadow.app.ui.theme.Palette

data class ChartSlice(val label: String, val value: Int, val color: Color)

/** Vertical bars with the value above and the label below each bar (Compose Canvas, no chart library). */
@Composable
fun BarChart(bars: List<ChartSlice>, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val max = (bars.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { b ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(compactCount(b.value), style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1)
                Box(
                    Modifier.fillMaxWidth(0.7f)
                        .fillMaxHeight((b.value.toFloat() / max * 0.75f).coerceAtLeast(0.01f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(b.color),
                )
                Text(b.label, style = MaterialTheme.typography.labelSmall, color = Palette.TextSecondary, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Donut chart + legend with counts (and percentages where [showPercent]; v1.3 profile: counts only). */
@Composable
fun DonutChart(slices: List<ChartSlice>, modifier: Modifier = Modifier, size: Dp = 150.dp, centerText: String? = null, showPercent: Boolean = true) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = this.size.minDimension * 0.18f
                val d = this.size.minDimension - stroke
                val topLeft = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
                var start = -90f
                if (slices.all { it.value == 0 }) {
                    drawArc(Palette.Divider, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
                }
                slices.filter { it.value > 0 }.forEach { s ->
                    val sweep = 360f * s.value / total
                    drawArc(s.color, start, sweep - 1.2f, false, topLeft, Size(d, d), style = Stroke(stroke))
                    start += sweep
                }
            }
            if (centerText != null) Text(centerText, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                    Spacer(Modifier.width(8.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (showPercent) "${compactCount(s.value)} (${(s.value * 100f / total).toInt()}%)" else compactCount(s.value),
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary, maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/** Horizontal bars (label, bar, value) — used for top genres. */
@Composable
fun HorizontalBars(rows: List<ChartSlice>, modifier: Modifier = Modifier) {
    val max = (rows.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Palette.Card)) {
                    Box(Modifier.fillMaxWidth(r.value.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(r.color))
                }
                Text(r.value.toString(), style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            }
        }
    }
}
