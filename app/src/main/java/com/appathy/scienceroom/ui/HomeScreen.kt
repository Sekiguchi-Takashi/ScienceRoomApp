package com.appathy.scienceroom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.engine.GameEvent
import com.appathy.scienceroom.engine.MissionEngine
import com.appathy.scienceroom.engine.Scene
import com.appathy.scienceroom.engine.PlanEngine
import com.appathy.scienceroom.engine.PlanStyle
import com.appathy.scienceroom.engine.ReviewEngine
import com.appathy.scienceroom.engine.RecommendEngine

@Composable
fun HomeScreen(game: Game, onNavigate: (String) -> Unit) {
    val state = game.state
    val content = game.content
    val now = System.currentTimeMillis()
    val research = RecommendEngine.currentResearch(content, state)
    val suggestions = RecommendEngine.top(content, state, now)
    val missions = MissionEngine.today(state)
    val goalId = state.currentGoal
    val goalTech = goalId?.let { content.techById[it] }
    val goalSteps = if (goalId == null) emptyList()
    else PlanEngine.plan(content, state, goalId, PlanStyle.SHORTEST).take(3)
    val dueCount = ReviewEngine.dueCount(content, state, now)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("科学室", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onNavigate("profile") }) { Text("👤 自分") }
            }
        }

        item {
            PanelCard {
                Text("科学レベル ${state.level}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.expInLevel / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Spacer(Modifier.height(8.dp))
                LabeledRow("覚えた元素", "${state.knownElements.size} / ${content.elements.size}")
                LabeledRow("見つけた素材", "${state.discoveredMaterials.size} / ${content.materials.size}")
                LabeledRow("完成した技術", "${state.completedTech.size} / ${content.technologies.size}")
            }
        }

        if (goalTech != null) {
            item { SectionTitle("目標：" + goalTech.name) }
            item {
                PanelCard {
                    if (goalSteps.isEmpty()) {
                        Text("条件がそろいました。技術タブで完成させましょう", fontSize = 14.sp)
                    } else {
                        goalSteps.forEachIndexed { i, step ->
                            Text(
                                (i + 1).toString() + ". [" + step.kind + "] " + step.label,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    TextButton(onClick = { onNavigate("tech") }) { Text("計画をすべて見る") }
                }
            }
        }

        item { SectionTitle("現在の研究") }
        item {
            PanelCard {
                if (research == null) {
                    Text("研究できる技術がありません。素材を集めましょう", fontSize = 14.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(research.tech.imageId, 40)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(research.tech.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(research.tech.description, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (research.missingMaterials.isNotEmpty()) {
                        LabeledRow("必要素材", research.missingMaterials.joinToString("、") {
                            content.materialName(it)
                        })
                    }
                    if (research.missingElements.isNotEmpty()) {
                        LabeledRow("必要知識", research.missingElements.joinToString("、") {
                            content.elementName(it)
                        })
                    }
                    if (research.missingReactions.isNotEmpty()) {
                        LabeledRow("必要な反応", research.missingReactions.joinToString("、") {
                            content.reactionById[it]?.name ?: it
                        })
                    }
                    if (research.ready) Text("条件がそろっています", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onNavigate("tech") }) { Text("技術ツリーを見る") }
                }
            }
        }

        item { SectionTitle("今週のできごと") }
        item {
            val ev = game.event
            val done = state.eventCount.coerceAtMost(ev.goal)
            PanelCard(modifier = Modifier.clickable {
                onNavigate(
                    when (ev.kind) {
                        GameEvent.Kind.EXPLORE -> "world"
                        GameEvent.Kind.STUDY -> "quiz"
                        GameEvent.Kind.EXPERIMENT -> "lab"
                    }
                )
            }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ev.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "あと " + game.eventDaysLeft + " 日",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    ev.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { done.toFloat() / ev.goal },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.eventClaimed)
                        "達成しました（経験値 +" + ev.rewardExp + "）"
                    else done.toString() + " / " + ev.goal + "　達成で経験値 +" + ev.rewardExp,
                    fontSize = 12.sp,
                    color = if (state.eventClaimed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (dueCount > 0) {
            item {
                PanelCard(modifier = Modifier.clickable { onNavigate("quiz") }) {
                    Text(
                        "復習どきの元素が " + dueCount + " 個",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "忘れかけたころに解き直すと、いちばん記憶に残ります",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            PanelCard {
                SceneSpeech(Scene.HOME, state.exp / 10)
            }
        }

        item { SectionTitle("おすすめの行動") }
        for (i in suggestions.indices) {
            item {
                val s = suggestions[i]
                PanelCard(modifier = Modifier.clickable { onNavigate(s.route) }) {
                    Text("${i + 1}. ${s.title}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(s.reason, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionTitle("今日のミッション") }
        item {
            PanelCard {
                missions.forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            (if (m.done) "☑ " else "☐ ") + m.text,
                            fontSize = 14.sp
                        )
                        Text(
                            m.current.coerceAtMost(m.goal).toString() + " / " + m.goal,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
