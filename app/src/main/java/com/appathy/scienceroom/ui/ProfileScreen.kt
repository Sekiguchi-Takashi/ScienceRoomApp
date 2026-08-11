package com.appathy.scienceroom.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.data.PlayerRepo
import com.appathy.scienceroom.engine.CivilizationEngine
import com.appathy.scienceroom.engine.HintEngine
import com.appathy.scienceroom.engine.LearningEngine
import com.appathy.scienceroom.engine.SkillEngine
import com.appathy.scienceroom.engine.TitleEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(game: Game, onClose: () -> Unit) {
    val state = game.state
    val content = game.content
    val now = System.currentTimeMillis()
    var confirmReset by remember { mutableStateOf(false) }
    var dataMessage by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    val knowledge = state.knownElements.size * 100 / content.elements.size
    val discovery = state.discoveredMaterials.size * 100 / content.materials.size
    val invention = state.completedTech.size * 100 / content.technologies.size
    val accuracy = if (state.quizCount == 0) 0 else state.quizCorrect * 100 / state.quizCount
    val skills = SkillEngine.compute(content, state)
    val thinking = SkillEngine.total(skills)

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
                Text(
                    TitleEngine.current(content, state),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                LabeledRow("科学知識", "$knowledge %")
                LabeledRow("発見率", "$discovery %")
                LabeledRow("発明力", "$invention %")
                LabeledRow("実験成功率", "${state.successRate()} %")
                LabeledRow("クイズ正答率", "$accuracy %")
                LabeledRow("科学的思考力", "$thinking")
            }
        }

        item { SectionTitle("称号") }
        item {
            PanelCard {
                TitleEngine.all(content, state).forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            (if (t.achieved) "🏅 " else "・") + t.name,
                            fontSize = 13.sp,
                            fontWeight = if (t.achieved) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            t.condition,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { SectionTitle("文明のルート") }
        item {
            PanelCard {
                Text(CivilizationEngine.label(state), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                CivilizationEngine.scores(state).forEach { pair ->
                    LabeledRow(pair.first, pair.second.toString() + " 技術")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "どの系統を先に伸ばすかで、解禁される素材と実験の順番が変わります",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { SectionTitle("科学的思考力") }
        item {
            PanelCard {
                skills.forEach { sk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(sk.name, fontSize = 13.sp, modifier = Modifier.width(72.dp))
                        LinearProgressIndicator(
                            progress = { sk.value / 100f },
                            modifier = Modifier.weight(1f).height(8.dp)
                        )
                        Text(
                            "${sk.value}",
                            fontSize = 12.sp,
                            modifier = Modifier.width(34.dp),
                            textAlign = TextAlign.End
                        )
                    }
                    Text(
                        sk.note, fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 72.dp, bottom = 4.dp)
                    )
                }
            }
        }

        item { SectionTitle("研究候補") }
        item {
            PanelCard {
                if (state.researchLeads.isEmpty()) {
                    Text(
                        "まだありません。決められた組み合わせ以外も試すと、思わぬ発見があります",
                        fontSize = 13.sp
                    )
                } else {
                    state.researchLeads.forEach { id ->
                        val r = content.reactionById[id]
                        if (r != null) {
                            Text(
                                "・${r.name}（" +
                                    r.inputs.joinToString("＋") { content.materialName(it) } + "）",
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
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
                    FilterChip(
                        selected = state.autoHint,
                        onClick = { game.update { s -> s.copy(autoHint = !s.autoHint) } },
                        label = { Text("自動", fontSize = 11.sp) }
                    )
                    (1..5).forEach { lv ->
                        FilterChip(
                            selected = !state.autoHint && state.hintLevel == lv,
                            onClick = {
                                game.update { s -> s.copy(hintLevel = lv, autoHint = false) }
                            },
                            label = { Text("Lv$lv", fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "いまのヒント Lv${HintEngine.effectiveLevel(state)}" +
                        if (state.autoHint) "（成績に合わせて自動調整中）" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item { SectionTitle("データ") }
        item {
            PanelCard {
                Text(
                    "進行データはこの端末に保存されています。アプリを入れ直す前に書き出しておくと、" +
                        "あとから同じ状態に戻せます",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(PlayerRepo.export(state)))
                        dataMessage = "クリップボードに書き出しました。メモアプリなどに貼って保管してください"
                    }) { Text("書き出す") }
                    TextButton(onClick = {
                        val raw = clipboard.getText()?.text
                        val loaded = if (raw == null) null else PlayerRepo.importFrom(raw)
                        if (loaded == null) {
                            dataMessage = "クリップボードから読み込めませんでした"
                        } else {
                            game.replace(loaded)
                            dataMessage = "読み込みました"
                        }
                    }) { Text("読み込む") }
                }
                if (dataMessage.isNotEmpty()) {
                    Text(
                        dataMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
