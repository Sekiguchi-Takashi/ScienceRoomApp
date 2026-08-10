package com.appathy.scienceroom.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.data.ExperimentInput
import com.appathy.scienceroom.data.ExperimentResult
import com.appathy.scienceroom.data.Rank
import com.appathy.scienceroom.engine.Equipment
import com.appathy.scienceroom.engine.ExperimentEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(game: Game, onNavigate: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("実験室") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("温度シミュレーター") })
        }
        if (tab == 0) ExperimentPane(game) else TemperaturePane(game)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExperimentPane(game: Game) {
    val content = game.content
    val state = game.state

    var selected by remember { mutableStateOf(listOf<String>()) }
    var temperature by remember { mutableStateOf(200f) }
    var duration by remember { mutableStateOf(2f) }
    var equipment by remember { mutableStateOf(Equipment.NONE) }
    var result by remember { mutableStateOf<ExperimentResult?>(null) }

    val owned = state.inventory.filter { it.value > 0 }.keys.toList()
    val equipments = Equipment.available(state)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionTitle("素材を選ぶ（持っているもの）") }

        if (owned.isEmpty()) {
            item {
                PanelCard {
                    Text("まだ素材を持っていません。世界タブから探索してみましょう", fontSize = 14.sp)
                }
            }
        } else {
            item {
                Column {
                    owned.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { id ->
                                val m = content.materialById[id]
                                FilterChip(
                                    selected = selected.contains(id),
                                    onClick = {
                                        selected = if (selected.contains(id)) selected - id
                                        else selected + id
                                    },
                                    label = {
                                        Text(
                                            "${m?.name ?: id} ×${state.inventory[id] ?: 0}",
                                            fontSize = 11.sp
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("条件") }
        item {
            PanelCard {
                Text("温度：${temperature.toInt()} ℃", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..1600f
                )
                Text(
                    "この器具で出せる上限：${Equipment.maxTemp(equipment)} ℃",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text("時間：${duration.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = duration,
                    onValueChange = { duration = it },
                    valueRange = 1f..8f,
                    steps = 6
                )
                Spacer(Modifier.height(6.dp))
                Text("器具", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(equipments) { e ->
                        FilterChip(
                            selected = equipment == e,
                            onClick = { equipment = e },
                            label = { Text(Equipment.label(e), fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val input = ExperimentInput(
                        materials = selected,
                        temperature = temperature.toInt(),
                        duration = duration.toInt(),
                        equipment = equipment
                    )
                    val r = ExperimentEngine.run(content, game.state, input)
                    game.update { ExperimentEngine.applyResult(content, it, input, r) }
                    result = r
                    if (r.rank == Rank.S || r.rank == Rank.A) selected = emptyList()
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("実験開始") }
        }

        item { SectionTitle("記録した反応") }
        item {
            PanelCard {
                if (state.discoveredReactions.isEmpty()) {
                    Text("まだ反応を記録していません", fontSize = 13.sp)
                } else {
                    state.discoveredReactions.forEach { id ->
                        val r = content.reactionById[id]
                        if (r != null) {
                            Text("・${r.name}（${r.principle}）", fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    val r = result
    if (r != null) {
        val success = r.rank == Rank.S || r.rank == Rank.A
        AlertDialog(
            onDismissRequest = { result = null },
            title = {
                Text((if (success) "🎉 " else "🤔 ") + r.title + "　［${r.rank}］")
            },
            text = {
                Column {
                    Text("観察", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(r.observation, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("なぜそうなったか", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(r.explanation, fontSize = 14.sp)
                    if (r.principle != "—") {
                        Spacer(Modifier.height(6.dp))
                        Text("科学原理：${r.principle}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (r.causes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("主な原因候補", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        r.causes.forEachIndexed { i, c ->
                            Text("${i + 1}. ${c.label}：${c.weight}", fontSize = 13.sp)
                        }
                    }
                    if (r.hint.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("ヒント：${r.hint}", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (r.newKnowledge.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        r.newKnowledge.forEach { Text("・$it", fontSize = 13.sp) }
                    }
                    val w = r.warning
                    if (w != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("⚠ $w", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("経験値 +${r.gainedExp}", fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { result = null }) { Text("閉じる") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemperaturePane(game: Game) {
    val content = game.content
    var substanceId by remember { mutableStateOf(content.temperatures.first().id) }
    var temp by remember { mutableStateOf(20f) }

    val behavior = content.temperatures.firstOrNull { it.id == substanceId }
    val range = behavior?.ranges?.firstOrNull { temp.toInt() in it.min..it.max }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionTitle("物質を選ぶ") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(content.temperatures) { b ->
                    FilterChip(
                        selected = substanceId == b.id,
                        onClick = { substanceId = b.id },
                        label = { Text(b.name, fontSize = 12.sp) }
                    )
                }
            }
        }

        item {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Thumb(behavior?.imageId ?: "", 56)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(behavior?.name ?: "", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${temp.toInt()} ℃", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                Slider(
                    value = temp,
                    onValueChange = { temp = it },
                    valueRange = -200f..2000f
                )
                Spacer(Modifier.height(6.dp))
                LabeledRow("状態", range?.state ?: "—")
                LabeledRow("変化", range?.change ?: "—")
                LabeledRow("危険度", dangerStars(range?.danger ?: 1))
                Spacer(Modifier.height(6.dp))
                Text("見た目の変化", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(range?.visible ?: "—", fontSize = 14.sp)
                val warn = range?.warning ?: ""
                if (warn.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("⚠ $warn", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
