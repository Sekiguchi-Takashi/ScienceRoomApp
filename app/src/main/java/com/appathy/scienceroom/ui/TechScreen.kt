package com.appathy.scienceroom.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.appathy.scienceroom.Feedback
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.engine.PlanEngine
import com.appathy.scienceroom.engine.PlanStyle
import com.appathy.scienceroom.engine.RoadmapEngine
import com.appathy.scienceroom.engine.Scene
import com.appathy.scienceroom.engine.TechnologyEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechScreen(game: Game, onNavigate: (String) -> Unit) {
    val content = game.content
    val state = game.state
    val statuses = TechnologyEngine.all(content, state)
    val tierMap = RoadmapEngine.tiers(content)
    var filter by remember { mutableStateOf("all") }
    val filtered = when (filter) {
        "ready" -> statuses.filter { it.ready }
        "open" -> statuses.filter { !it.completed && it.unlocked }
        "done" -> statuses.filter { it.completed }
        else -> statuses
    }
    val grouped = filtered.groupBy { tierMap[it.tech.id] ?: 0 }
    val tierKeys = grouped.keys.sorted()
    var completedName by remember { mutableStateOf<String?>(null) }
    var planFor by remember { mutableStateOf<String?>(null) }
    var planStyle by remember { mutableStateOf(PlanStyle.SHORTEST) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("科学技術ロードマップ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "技術は一本道ではなく分岐します。段が進むほど前提が増えます",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "all" to "すべて " + statuses.size,
                    "ready" to "研究可能 " + statuses.count { it.ready },
                    "open" to "着手中",
                    "done" to "完成 " + statuses.count { it.completed }
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                PanelCard { Text("この条件に当てはまる技術はありません", fontSize = 14.sp) }
            }
        }

        for (tier in tierKeys) {
        item {
            SectionTitle(
                "第${tier + 1}段　" +
                    (grouped[tier] ?: emptyList()).count { it.completed }.toString() +
                    " / " + (grouped[tier] ?: emptyList()).size
            )
        }
        items(grouped[tier] ?: emptyList()) { st ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 火や炉の技術は、完成しているとゆらいで見える
                    val fiery = st.completed && st.tech.id in setOf(
                        "fire", "kiln", "charcoal", "iron", "forging", "crucible"
                    )
                    if (fiery) Flicker { Thumb(st.tech.imageId, 48) }
                    else Thumb(st.tech.imageId, 48)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(st.tech.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    st.completed -> "完成"
                                    st.ready -> "研究可能"
                                    st.unlocked -> "条件不足"
                                    else -> "未解放"
                                },
                                fontSize = 12.sp,
                                color = if (st.ready) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            st.tech.description, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (!st.completed) {
                    if (st.missingTech.isNotEmpty()) {
                        LabeledRow("前提技術", st.missingTech.joinToString("、") { content.techName(it) })
                    }
                    if (st.missingMaterials.isNotEmpty()) {
                        LabeledRow("不足素材", st.missingMaterials.joinToString("、") {
                            content.materialName(it)
                        })
                    }
                    if (st.missingElements.isNotEmpty()) {
                        LabeledRow("不足知識", st.missingElements.joinToString("、") {
                            content.elementName(it)
                        })
                    }
                    if (st.missingReactions.isNotEmpty()) {
                        LabeledRow("必要な反応", st.missingReactions.joinToString("、") {
                            content.reactionById[it]?.name ?: it
                        })
                    }
                    if (st.tech.unlocksLocations.isNotEmpty()) {
                        LabeledRow("解禁される地域", st.tech.unlocksLocations.joinToString("、") {
                            content.locationById[it]?.name ?: it
                        })
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                game.update { TechnologyEngine.complete(content, it, st.tech) }
                                game.feedback(Feedback.Kind.UNLOCK)
                                completedName = st.tech.name
                            },
                            enabled = st.ready
                        ) { Text("研究する") }
                        TextButton(onClick = { planFor = st.tech.id }) { Text("研究計画") }
                        if (st.missingElements.isNotEmpty()) {
                            TextButton(onClick = { onNavigate("quiz") }) { Text("元素を覚える") }
                        } else if (st.missingMaterials.isNotEmpty()) {
                            TextButton(onClick = { onNavigate("world") }) { Text("探索") }
                        } else if (st.missingReactions.isNotEmpty()) {
                            TextButton(onClick = { onNavigate("lab") }) { Text("実験") }
                        }
                    }
                } else {
                    LabeledRow("難易度", dangerStars(st.tech.difficulty))
                }

                val children = RoadmapEngine.children(content, st.tech.id)
                if (children.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "└→ " + children.joinToString("、") { it.name },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    val goalId = planFor
    if (goalId != null) {
        val goal = content.techById[goalId]
        val steps = PlanEngine.plan(content, state, goalId, planStyle)
        AlertDialog(
            onDismissRequest = { planFor = null },
            title = { Text("研究計画：" + (goal?.name ?: "")) },
            text = {
                Column {
                    Text(
                        "目標から必要な知識・素材・実験・技術を逆算しました。並べ方を選べます",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlanStyle.entries.forEach { ps ->
                            FilterChip(
                                selected = planStyle == ps,
                                onClick = { planStyle = ps },
                                label = { Text(ps.label, fontSize = 10.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (steps.isEmpty()) {
                        Text("すでに条件を満たしています", fontSize = 14.sp)
                    } else {
                        steps.forEachIndexed { i, step ->
                            Text(
                                (i + 1).toString() + ". [" + step.kind + "] " + step.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (step.detail.isNotEmpty()) {
                                Text(
                                    "　　" + step.detail,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("全 " + steps.size + " 手", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    game.update { it.copy(currentGoal = goalId) }
                    planFor = null
                }) { Text("これを目標にする") }
            },
            dismissButton = { TextButton(onClick = { planFor = null }) { Text("閉じる") } }
        )
    }

    val name = completedName
    if (name != null) {
        AlertDialog(
            onDismissRequest = { completedName = null },
            title = { Text("技術を解禁した") },
            text = {
                Column {
                    val tech = content.technologies.firstOrNull { it.name == name }
                    if (tech != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Burst(key = name) { Thumb(tech.imageId, 96) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    SceneSpeech(Scene.TECH_UNLOCK, state.completedTech.size)
                    Spacer(Modifier.height(8.dp))
                    Text("「" + name + "」が完成しました。新しい素材・地域・実験が使えるようになります")
                }
            },
            confirmButton = { TextButton(onClick = { completedName = null }) { Text("閉じる") } }
        )
    }
}
