package com.appathy.scienceroom.ui

import com.appathy.scienceroom.engine.Cast
import com.appathy.scienceroom.engine.TeacherEngine
import com.appathy.scienceroom.engine.Assist
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Feedback
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.data.ExperimentInput
import com.appathy.scienceroom.data.ExperimentLog
import com.appathy.scienceroom.data.ExperimentResult
import com.appathy.scienceroom.data.Rank
import com.appathy.scienceroom.engine.Equipment
import com.appathy.scienceroom.engine.EventEngine
import com.appathy.scienceroom.engine.GameEvent
import com.appathy.scienceroom.engine.ExperimentEngine
import com.appathy.scienceroom.engine.RatioEngine
import com.appathy.scienceroom.engine.Scene

/** 実験の入力条件。ノートから復元できるようタブをまたいで保持する */
class LabInput {
    var selected by mutableStateOf(listOf<String>())
    var quantities by mutableStateOf(mapOf<String, Int>())
    var temperature by mutableStateOf(200f)
    var duration by mutableStateOf(2f)
    var equipment by mutableStateOf(Equipment.NONE)

    fun restore(log: ExperimentLog) {
        selected = log.materials
        quantities = log.quantities
        temperature = log.temperature.toFloat()
        duration = log.duration.toFloat()
        equipment = log.equipment ?: Equipment.NONE
    }

    fun clear() {
        selected = emptyList()
        quantities = emptyMap()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(game: Game, onNavigate: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val input = remember { LabInput() }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("実験室") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("温度") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("ノート") })
        }
        when (tab) {
            0 -> ExperimentPane(game, input)
            1 -> TemperaturePane(game)
            else -> NotebookPane(game) { log ->
                input.restore(log)
                tab = 0
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExperimentPane(game: Game, input: LabInput) {
    val content = game.content
    val state = game.state
    var result by remember { mutableStateOf<ExperimentResult?>(null) }

    val owned = state.inventory.filter { it.value > 0 }.keys.toList()
    val equipments = Equipment.available(state)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            val assist = Assist.entries.firstOrNull { it.id == state.assistLevel } ?: Assist.GUIDED
            PanelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("実験の進めかた", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Assist.entries.forEach { a ->
                        FilterChip(
                            selected = assist == a,
                            onClick = { game.update { s2 -> s2.copy(assistLevel = a.id) } },
                            label = { Text(a.label, fontSize = 10.sp) }
                        )
                    }
                }
                Text(
                    assist.note,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (assist != Assist.SELF) {
                    val target = TeacherEngine.nextLesson(content, state)
                    if (target != null) {
                        Spacer(Modifier.height(10.dp))
                        val step = state.lessonStep.coerceIn(0, 3)
                        val lesson = TeacherEngine.lesson(content, target, step)
                        Text(
                            "第" + (step + 1) + "歩　" + lesson.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        SpeechRow(lesson.speaker, lesson.body, 56)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (step > 0) {
                                TextButton(onClick = {
                                    game.update { s2 -> s2.copy(lessonStep = step - 1) }
                                }) { Text("前へ") }
                            }
                            if (!lesson.isLast) {
                                Button(onClick = {
                                    game.feedback(Feedback.Kind.TAP)
                                    game.update { s2 -> s2.copy(lessonStep = step + 1) }
                                }) { Text("次を教わる") }
                            } else {
                                Button(onClick = {
                                    input.selected = target.inputs
                                    input.quantities = target.inputs.associateWith { id ->
                                        target.ratios?.get(id) ?: 1
                                    }
                                    input.temperature =
                                        ((target.minTemp + target.maxTemp) / 2).toFloat()
                                    input.duration = (target.minDuration + 1)
                                        .coerceAtMost(8).toFloat()
                                    input.equipment = target.equipment ?: Equipment.NONE
                                    game.feedback(Feedback.Kind.DISCOVER)
                                }) { Text("この条件を用意する") }
                            }
                            TextButton(onClick = {
                                game.update { s2 -> s2.copy(lessonStep = 0) }
                            }) { Text("最初から") }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("いま教えられることはもうないよ。よくここまで来たね", fontSize = 13.sp)
                    }
                }
            }
        }

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
                                FilterChip(
                                    selected = input.selected.contains(id),
                                    onClick = {
                                        if (input.selected.contains(id)) {
                                            input.selected = input.selected - id
                                            input.quantities = input.quantities - id
                                        } else {
                                            input.selected = input.selected + id
                                            input.quantities = input.quantities + (id to 1)
                                        }
                                    },
                                    label = {
                                        Text(
                                            content.materialName(id) + " ×" +
                                                (state.inventory[id] ?: 0),
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

        if (input.selected.isNotEmpty()) {
            item { SectionTitle("分量") }
            item {
                PanelCard {
                    Text(
                        "反応によっては入れる量のつり合いが結果を変えます",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    input.selected.forEach { id ->
                        val owns = state.inventory[id] ?: 0
                        val n = input.quantities[id] ?: 1
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                content.materialName(id) + "（所持 " + owns + "）",
                                fontSize = 13.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    if (n > 1) input.quantities =
                                        input.quantities + (id to (n - 1))
                                }) { Text("−", fontSize = 18.sp) }
                                Text(
                                    n.toString(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(28.dp),
                                    textAlign = TextAlign.Center
                                )
                                TextButton(onClick = {
                                    if (n < 9) input.quantities =
                                        input.quantities + (id to (n + 1))
                                }) { Text("＋", fontSize = 16.sp) }
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("条件") }
        item {
            PanelCard {
                Text(
                    "温度：" + input.temperature.toInt() + " ℃",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = input.temperature,
                    onValueChange = { input.temperature = it },
                    valueRange = 0f..1600f
                )
                Text(
                    "この器具で出せる上限：" + Equipment.maxTemp(input.equipment) + " ℃",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val assistNow = Assist.entries.firstOrNull { it.id == state.assistLevel }
                    ?: Assist.GUIDED
                val advice = TeacherEngine.temperatureAdvice(
                    content, assistNow, input.selected, input.temperature.toInt()
                )
                if (advice.isNotEmpty()) {
                    Text(advice, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "時間：" + input.duration.toInt(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = input.duration,
                    onValueChange = { input.duration = it },
                    valueRange = 1f..8f,
                    steps = 6
                )
                Spacer(Modifier.height(6.dp))
                Text("器具", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(equipments) { e ->
                        FilterChip(
                            selected = input.equipment == e,
                            onClick = { input.equipment = e },
                            label = { Text(Equipment.label(e), fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val payload = ExperimentInput(
                        materials = input.selected,
                        quantities = input.quantities,
                        temperature = input.temperature.toInt(),
                        duration = input.duration.toInt(),
                        equipment = input.equipment
                    )
                    val r = ExperimentEngine.run(content, game.state, payload, game.event)
                    game.update {
                        var s2 = ExperimentEngine.applyResult(content, it, payload, r)
                        if (game.event.kind == GameEvent.Kind.EXPERIMENT) {
                            s2 = EventEngine.advance(s2, game.event, 1)
                        }
                        s2
                    }
                    result = r
                    game.feedback(
                        if (r.rank == Rank.S || r.rank == Rank.A) Feedback.Kind.SUCCESS
                        else Feedback.Kind.FAIL
                    )
                    if (r.rank == Rank.S || r.rank == Rank.A) {
                        input.clear()
                        game.update { s2 -> s2.copy(lessonStep = 0) }
                    }
                },
                enabled = input.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("実験開始") }
        }

        if (state.favorites.isNotEmpty()) {
            item { SectionTitle("覚えておいた手順") }
            item {
                PanelCard {
                    state.favorites.reversed().forEach { fav ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    fav.materials.joinToString("＋") {
                                        content.materialName(it) + "×" + (fav.quantities[it] ?: 1)
                                    },
                                    fontSize = 13.sp
                                )
                                Text(
                                    fav.temperature.toString() + "℃／時間" + fav.duration +
                                        "／" + Equipment.label(fav.equipment),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { input.restore(fav) }) { Text("読み込む") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("研究候補（偶然の発見）") }
        item {
            PanelCard {
                val leads = state.researchLeads.filter { !state.discoveredReactions.contains(it) }
                if (leads.isEmpty()) {
                    Text(
                        "決められた組み合わせ以外も試してみましょう。思わぬ手がかりが出ることがあります",
                        fontSize = 13.sp
                    )
                } else {
                    leads.forEach { id ->
                        val r = content.reactionById[id]
                        if (r != null) {
                            Text(
                                "・" + r.name + "：" +
                                    r.inputs.joinToString("＋") { content.materialName(it) },
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
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
            title = { Text((if (success) "🎉 " else "🤔 ") + r.title + "　［" + r.rank + "］") },
            text = {
                Column {
                    val scene = when (r.rank) {
                        Rank.S, Rank.A -> Scene.EXPERIMENT_SUCCESS
                        Rank.B -> Scene.EXPERIMENT_PARTIAL
                        else -> if (r.leadReactionId != null) Scene.DISCOVER_LEAD
                        else Scene.EXPERIMENT_FAIL
                    }
                    val product = r.productId?.let { content.materialById[it] }
                    if (product != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Burst(key = r) { Thumb(product.imageId, 92) }
                        }
                        Spacer(Modifier.height(8.dp))
                    } else if (!success) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Shake(key = r) { Thumb("eq_crucible", 72) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    SceneSpeech(scene, game.state.experimentCount)
                    Spacer(Modifier.height(8.dp))
                    Text("観察", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(r.observation, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("なぜそうなったか", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(r.explanation, fontSize = 14.sp)
                    if (r.principle != "—") {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "科学原理：" + r.principle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (r.causes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("主な原因候補", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        r.causes.forEachIndexed { i, c ->
                            Text((i + 1).toString() + ". " + c.label + "：" + c.weight, fontSize = 13.sp)
                        }
                    }
                    if (r.causes.isNotEmpty()) {
                        val tip = TeacherEngine.advice(r.causes)
                        if (tip.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            SpeechRow(Cast.daichi, tip, 48)
                        }
                    }
                    if (r.hint.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "ヒント：" + r.hint,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (r.newKnowledge.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        r.newKnowledge.forEach { Text("・" + it, fontSize = 13.sp) }
                    }
                    val w = r.warning
                    if (w != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("⚠ " + w, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("経験値 +" + r.gainedExp, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { result = null }) { Text("閉じる") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotebookPane(game: Game, onReplay: (ExperimentLog) -> Unit) {
    val content = game.content
    val state = game.state
    var filter by remember { mutableStateOf("all") }

    val logs = when (filter) {
        "success" -> state.notebook.filter { it.rank == "S" || it.rank == "A" }
        "fail" -> state.notebook.filter { it.rank != "S" && it.rank != "A" }
        else -> state.notebook
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("実験ノート", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "条件をすべて残しているので、同じ実験をやり直せます",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filter == "all",
                    onClick = { filter = "all" },
                    label = { Text("すべて " + state.notebook.size, fontSize = 11.sp) }
                )
                FilterChip(
                    selected = filter == "success",
                    onClick = { filter = "success" },
                    label = { Text("成功", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = filter == "fail",
                    onClick = { filter = "fail" },
                    label = { Text("失敗", fontSize = 11.sp) }
                )
            }
        }

        if (logs.isEmpty()) {
            item {
                PanelCard { Text("まだ記録がありません", fontSize = 14.sp) }
            }
        } else {
            items(logs) { log ->
                PanelCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(log.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "［" + log.rank + "］",
                            fontSize = 13.sp,
                            color = if (log.rank == "S" || log.rank == "A")
                                MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        log.materials.joinToString("＋") {
                            content.materialName(it) + "×" + (log.quantities[it] ?: 1)
                        },
                        fontSize = 13.sp
                    )
                    LabeledRow(
                        "条件",
                        log.temperature.toString() + "℃／時間" + log.duration +
                            "／" + Equipment.label(log.equipment)
                    )
                    val reaction = log.reactionId?.let { content.reactionById[it] }
                    if (reaction != null && state.discoveredReactions.contains(reaction.id)) {
                        val ratio = RatioEngine.describe(content, reaction)
                        if (ratio.isNotEmpty()) LabeledRow("正解の比", ratio)
                    }
                    if (log.causes.isNotEmpty()) {
                        Text(
                            "原因候補：" + log.causes.joinToString("、"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onReplay(log) }) { Text("読み込む") }
                        val saved = state.favorites.any {
                            it.materials == log.materials &&
                                it.temperature == log.temperature &&
                                it.duration == log.duration &&
                                it.equipment == log.equipment
                        }
                        TextButton(onClick = {
                            game.update { s2 ->
                                if (saved) {
                                    s2.copy(favorites = s2.favorites.filterNot {
                                        it.materials == log.materials &&
                                            it.temperature == log.temperature &&
                                            it.duration == log.duration &&
                                            it.equipment == log.equipment
                                    })
                                } else {
                                    s2.copy(favorites = (s2.favorites + log).takeLast(10))
                                }
                            }
                        }) { Text(if (saved) "★ 登録済み" else "☆ 覚えておく") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
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
                        Text(
                            temp.toInt().toString() + " ℃",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                    Text("⚠ " + warn, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
