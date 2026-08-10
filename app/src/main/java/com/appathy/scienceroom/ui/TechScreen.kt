package com.appathy.scienceroom.ui

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
import com.appathy.scienceroom.engine.TechnologyEngine

@Composable
fun TechScreen(game: Game, onNavigate: (String) -> Unit) {
    val content = game.content
    val state = game.state
    val statuses = TechnologyEngine.all(content, state)
    var completedName by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("科学技術ロードマップ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "条件を満たした技術から順に解禁されます",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(statuses) { st ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(st.tech.imageId, 48)
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
                                completedName = st.tech.name
                            },
                            enabled = st.ready
                        ) { Text("研究する") }
                        if (st.missingElements.isNotEmpty()) {
                            TextButton(onClick = { onNavigate("quiz") }) { Text("元素を覚える") }
                        } else if (st.missingMaterials.isNotEmpty()) {
                            TextButton(onClick = { onNavigate("world") }) { Text("探索する") }
                        } else if (st.missingReactions.isNotEmpty()) {
                            TextButton(onClick = { onNavigate("lab") }) { Text("実験する") }
                        }
                    }
                } else {
                    LabeledRow("難易度", dangerStars(st.tech.difficulty))
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    val name = completedName
    if (name != null) {
        AlertDialog(
            onDismissRequest = { completedName = null },
            title = { Text("技術を解禁した") },
            text = { Text("「$name」が完成しました。新しい素材・地域・実験が使えるようになります") },
            confirmButton = { TextButton(onClick = { completedName = null }) { Text("閉じる") } }
        )
    }
}
