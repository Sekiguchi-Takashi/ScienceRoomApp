package com.appathy.scienceroom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.engine.LearningEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(game: Game, onClose: () -> Unit) {
    val state = game.state
    val content = game.content
    val now = System.currentTimeMillis()
    var confirmReset by remember { mutableStateOf(false) }

    val knowledge = state.knownElements.size * 100 / content.elements.size
    val discovery = state.discoveredMaterials.size * 100 / content.materials.size
    val invention = state.completedTech.size * 100 / content.technologies.size
    val accuracy = if (state.quizCount == 0) 0 else state.quizCorrect * 100 / state.quizCount
    val thinking = (knowledge + discovery + invention + state.successRate()) / 4

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("自分", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose) { Text("閉じる") }
            }
        }

        item {
            PanelCard {
                Text("科学者レベル ${state.level}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LabeledRow("科学知識", "$knowledge %")
                LabeledRow("発見率", "$discovery %")
                LabeledRow("発明力", "$invention %")
                LabeledRow("実験成功率", "${state.successRate()} %")
                LabeledRow("クイズ正答率", "$accuracy %")
                LabeledRow("科学的思考力", "$thinking")
            }
        }

        item { SectionTitle("活動の記録") }
        item {
            PanelCard {
                LabeledRow("探索した回数", state.exploreCount.toString())
                LabeledRow("実験した回数", state.experimentCount.toString())
                LabeledRow("成功 / 失敗", "${state.successCount} / ${state.failCount}")
                LabeledRow("解いた問題", state.quizCount.toString())
                LabeledRow("記録した反応", state.discoveredReactions.size.toString())
            }
        }

        item { SectionTitle("いま忘れやすい元素") }
        item {
            PanelCard {
                LearningEngine.weakElements(content, state, now, 3).forEach { e ->
                    val l = state.learning[e.id]
                    LabeledRow(
                        "${e.symbol}　${e.name}",
                        if (l == null || l.answered() == 0) "未学習"
                        else "${l.answered()}問・連続${l.streak}"
                    )
                }
            }
        }

        item { SectionTitle("ヒントの詳しさ") }
        item {
            PanelCard {
                Text(
                    "失敗したときにAIがどこまで教えるかを決めます。低いほど自分で考える余地が残ります",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { lv ->
                        FilterChip(
                            selected = state.hintLevel == lv,
                            onClick = { game.update { s -> s.copy(hintLevel = lv) } },
                            label = { Text("Lv$lv", fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        item { SectionTitle("データ") }
        item {
            PanelCard {
                Text("進行データはこの端末に保存されています", fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { confirmReset = true }) { Text("最初からやり直す") }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("データを消去しますか") },
            text = { Text("覚えた元素、集めた素材、完成した技術がすべて消えます。元には戻せません") },
            confirmButton = {
                TextButton(onClick = {
                    game.reset()
                    confirmReset = false
                    onClose()
                }) { Text("消去する") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("やめる") }
            }
        )
    }
}
