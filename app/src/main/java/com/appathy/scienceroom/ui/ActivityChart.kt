package com.appathy.scienceroom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.data.DailyStat

private val QuizColor = Color(0xFF2E5A72)
private val ExperimentColor = Color(0xFFC2611F)
private val ExploreColor = Color(0xFF4E6B4A)

/**
 * 直近14日の活動量を積み上げ棒で描く。
 * 追加ライブラリを入れず Canvas だけで済ませる。
 */
@Composable
fun ActivityChart(stats: List<DailyStat>, modifier: Modifier = Modifier) {
    val days = stats.takeLast(14)
    if (days.isEmpty()) {
        Text("まだ記録がありません", fontSize = 13.sp)
        return
    }

    val maxValue = days.maxOf { it.activity() }.coerceAtLeast(1)

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / days.size
            val barWidth = slot * 0.62f
            val gap = (slot - barWidth) / 2f

            // 目盛り線
            for (i in 0..2) {
                val y = size.height * i / 2f
                drawRect(
                    color = Color(0x14000000),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, 1f)
                )
            }

            days.forEachIndexed { index, stat ->
                var bottom = size.height
                val segments = listOf(
                    stat.quizAnswered to QuizColor,
                    stat.experiments to ExperimentColor,
                    stat.explores to ExploreColor
                )
                segments.forEach { (value, color) ->
                    if (value > 0) {
                        val h = size.height * value / maxValue
                        drawRect(
                            color = color,
                            topLeft = Offset(index * slot + gap, bottom - h),
                            size = Size(barWidth, h)
                        )
                        bottom -= h
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                shortDate(days.first().date),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "最大 " + maxValue,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                shortDate(days.last().date),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot("クイズ", QuizColor)
            LegendDot("実験", ExperimentColor)
            LegendDot("探索", ExploreColor)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp)
    }
}

/** "2026-08-11" を "8/11" にする */
private fun shortDate(date: String): String {
    val parts = date.split("-")
    if (parts.size != 3) return date
    return parts[1].trimStart('0') + "/" + parts[2].trimStart('0')
}
