package com.appathy.scienceroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.data.Element

private const val GROUPS = 18
private const val PERIODS = 7

/** 分類ごとの色。覚えるときの手がかりにする */
private fun categoryColor(category: String): Color = when (category) {
    "アルカリ金属" -> Color(0xFFD9784E)
    "アルカリ土類金属" -> Color(0xFFD9A441)
    "遷移金属" -> Color(0xFFB08A6B)
    "金属" -> Color(0xFF8E9BA6)
    "半金属" -> Color(0xFF6E9E8A)
    "非金属" -> Color(0xFF5B8FA8)
    "ハロゲン" -> Color(0xFF7E6CA8)
    "貴ガス" -> Color(0xFF9E6E86)
    else -> Color(0xFF9A9A9A)
}

private val categories = listOf(
    "アルカリ金属", "アルカリ土類金属", "遷移金属", "金属",
    "半金属", "非金属", "ハロゲン", "貴ガス"
)

@Composable
fun PeriodicTable(
    elements: List<Element>,
    known: Set<String>,
    onSelect: (Element) -> Unit
) {
    val byPosition = elements.filter { it.group in 1..GROUPS && it.period in 1..PERIODS }
        .associateBy { it.period * 100 + it.group }
    val scroll = rememberScrollState()

    Column {
        Text(
            "横にスクロールできます。色は分類、灰色はまだ覚えていない元素です",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.horizontalScroll(scroll)) {
            Column {
                // 族番号の見出し
                Row {
                    Spacer(Modifier.width(20.dp))
                    for (g in 1..GROUPS) {
                        Text(
                            g.toString(),
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(34.dp)
                        )
                    }
                }
                for (p in 1..PERIODS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            p.toString(),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(20.dp),
                            textAlign = TextAlign.Center
                        )
                        for (g in 1..GROUPS) {
                            val element = byPosition[p * 100 + g]
                            Cell(element, element != null && known.contains(element.id), onSelect)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("分類", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        categories.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { c ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(categoryColor(c))
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(c, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(element: Element?, known: Boolean, onSelect: (Element) -> Unit) {
    if (element == null) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .padding(1.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x14000000))
        )
        return
    }

    val base = if (known) categoryColor(element.category) else Color(0xFFB6B0A6)
    Box(
        modifier = Modifier
            .size(34.dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(base)
            .border(0.5.dp, Color(0x33000000), RoundedCornerShape(3.dp))
            .clickable { onSelect(element) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                element.atomicNumber.toString(),
                fontSize = 7.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                if (known) element.symbol else "?",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
