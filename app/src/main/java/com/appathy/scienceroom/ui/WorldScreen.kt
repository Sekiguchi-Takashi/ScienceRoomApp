package com.appathy.scienceroom.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Feedback
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.engine.ExploreOutcome
import com.appathy.scienceroom.engine.EventEngine
import com.appathy.scienceroom.engine.ExplorationEngine
import com.appathy.scienceroom.engine.GameEvent

@Composable
fun WorldScreen(game: Game, onNavigate: (String) -> Unit) {
    val content = game.content
    val state = game.state
    var outcome by remember { mutableStateOf<ExploreOutcome?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("世界", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("地域を選んで探索し、素材を見つけよう", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        items(content.locations) { loc ->
            val unlocked = state.unlockedLocations.contains(loc.id)
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(130.dp)) {
                    AssetImage(
                        name = loc.imageId,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (!unlocked) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color(0xCC1B1B1B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔒 未解放", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(loc.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("危険度 ${dangerStars(loc.danger)}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(loc.description, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val known = loc.materials.filter { state.discoveredMaterials.contains(it) }
                    Text(
                        "発見済みの素材：" + if (known.isEmpty()) "まだない"
                        else known.joinToString("、") { content.materialName(it) },
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (unlocked) {
                        Button(onClick = {
                            val o = ExplorationEngine.explore(
                                content, game.state, loc, game.event
                            )
                            game.update {
                                var s2 = ExplorationEngine.applyOutcome(it, o)
                                if (game.event.kind == GameEvent.Kind.EXPLORE &&
                                    game.event.targetId == loc.id && o.foundMaterialId != null
                                ) {
                                    s2 = EventEngine.advance(s2, game.event, 1)
                                }
                                s2
                            }
                            game.feedback(
                                if (o.isNew) Feedback.Kind.DISCOVER
                                else if (o.foundMaterialId != null) Feedback.Kind.TAP
                                else Feedback.Kind.FAIL
                            )
                            outcome = o
                        }) { Text("探索する") }
                    } else {
                        val by = loc.unlockedBy
                        Text(
                            "「${if (by != null) content.techName(by) else "?"}」を完成させると入れる",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    val o = outcome
    if (o != null) {
        val mat = o.foundMaterialId?.let { content.materialById[it] }
        AlertDialog(
            onDismissRequest = { outcome = null },
            title = { Text(if (o.foundMaterialId != null) "探索結果" else "収穫なし") },
            text = {
                Column {
                    Text(o.message, fontSize = 15.sp)
                    if (mat != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Thumb(mat.imageId, 56)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("${mat.name} ×${o.amount}", fontWeight = FontWeight.Bold)
                                Text(mat.composition, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(mat.note, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "関連する元素：" + mat.elements.joinToString("、") { content.elementName(it) },
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { outcome = null }) { Text("閉じる") } },
            dismissButton = {
                if (mat != null) TextButton(onClick = {
                    outcome = null
                    onNavigate("lab")
                }) { Text("実験する") }
            }
        )
    }
}
