package com.appathy.scienceroom.ui

import com.appathy.scienceroom.engine.LinkEngine
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.appathy.scienceroom.data.Element
import com.appathy.scienceroom.data.GameMaterial

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncyclopediaScreen(game: Game, onNavigate: (String) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var element by remember { mutableStateOf<Element?>(null) }
    var material by remember { mutableStateOf<GameMaterial?>(null) }
    var table by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var elementSort by remember { mutableStateOf("number") }
    var materialSort by remember { mutableStateOf("name") }

    val content = game.content
    val state = game.state

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("元素") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("素材") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("化合物") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("反応") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (tab) {
                0 -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "覚えた元素 ${state.knownElements.size} / ${content.elements.size}",
                                fontSize = 14.sp
                            )
                            Button(onClick = { onNavigate("quiz") }) { Text("クイズを解く") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !table,
                                onClick = { table = false },
                                label = { Text("一覧", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = table,
                                onClick = { table = true },
                                label = { Text("周期表", fontSize = 11.sp) }
                            )
                        }
                    }

                    if (table) {
                        item {
                            PanelCard {
                                PeriodicTable(
                                    elements = content.elements,
                                    known = state.knownElements
                                ) { e -> if (state.knownElements.contains(e.id)) element = e }
                            }
                        }
                    } else {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "number" to "原子番号",
                                "category" to "分類",
                                "known" to "未習得から"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = elementSort == key,
                                    onClick = { elementSort = key },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                    items(
                        when (elementSort) {
                            "category" -> content.elements.sortedWith(
                                compareBy({ it.category }, { it.atomicNumber })
                            )
                            "known" -> content.elements.sortedWith(
                                compareBy(
                                    { state.knownElements.contains(it.id) },
                                    { it.atomicNumber }
                                )
                            )
                            else -> content.elements.sortedBy { it.atomicNumber }
                        }
                    ) { e ->
                        val known = state.knownElements.contains(e.id)
                        PanelCard(modifier = Modifier.clickable { if (known) element = e }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (known) Thumb(e.imageId, 46)
                                else Text("？", fontSize = 26.sp, modifier = Modifier.width(46.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (known) "${e.symbol}　${e.name}" else "???",
                                        fontSize = 17.sp, fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (known) "原子番号 ${e.atomicNumber}・${e.category}"
                                        else "クイズで覚えると開放されます",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    }
                }

                1 -> {
                    item {
                        Text(
                            "見つけた素材 ${state.discoveredMaterials.size} / ${content.materials.size}",
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("名前や組成でしぼる", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "name" to "名前",
                                "rarity" to "希少度",
                                "kind" to "区分",
                                "owned" to "所持数"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = materialSort == key,
                                    onClick = { materialSort = key },
                                    label = { Text(label, fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                    items(
                        content.materials.filter {
                            query.isEmpty() || it.name.contains(query) ||
                                it.composition.contains(query, ignoreCase = true)
                        }.let { list ->
                            when (materialSort) {
                                "rarity" -> list.sortedByDescending { it.rarity }
                                "kind" -> list.sortedWith(
                                    compareBy({ it.kind }, { it.name })
                                )
                                "owned" -> list.sortedByDescending {
                                    state.inventory[it.id] ?: 0
                                }
                                else -> list
                            }
                        }
                    ) { m ->
                        val found = state.discoveredMaterials.contains(m.id)
                        PanelCard(modifier = Modifier.clickable { if (found) material = m }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (found) Thumb(m.imageId, 46)
                                else Text("？", fontSize = 26.sp, modifier = Modifier.width(46.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        if (found) m.name else "???",
                                        fontSize = 17.sp, fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (found) "${m.composition}　所持 ${state.inventory[m.id] ?: 0}"
                                        else "探索や実験で見つかります",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        Text(
                            "物質は単体・化合物・混合物に分けられます。見つけた素材だけ並びます",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    for (kind in listOf("単体", "化合物", "混合物")) {
                        item { SectionTitle(kind) }
                        val list = content.materials.filter {
                            it.kind == kind && state.discoveredMaterials.contains(it.id)
                        }
                        if (list.isEmpty()) {
                            item {
                                PanelCard {
                                    Text("まだ見つけていません", fontSize = 13.sp)
                                }
                            }
                        } else {
                            items(list) { m ->
                                PanelCard(modifier = Modifier.clickable { material = m }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Thumb(m.imageId, 40)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                "${m.name}　${m.composition}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                m.elements.joinToString("・") {
                                                    content.elementById[it]?.symbol ?: it
                                                },
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    item {
                        Text(
                            "記録した反応 ${state.discoveredReactions.size} / ${content.reactions.size}",
                            fontSize = 14.sp
                        )
                    }
                    items(content.reactions) { r ->
                        val found = state.discoveredReactions.contains(r.id)
                        PanelCard {
                            Text(
                                if (found) r.name else "???",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                            if (found) {
                                Text(
                                    r.inputs.joinToString(" ＋ ") { content.materialName(it) } +
                                        " → " + content.materialName(r.product),
                                    fontSize = 13.sp
                                )
                                LabeledRow("条件", "${r.minTemp}〜${r.maxTemp}℃／時間${r.minDuration}以上")
                                val ratio = com.appathy.scienceroom.engine.RatioEngine
                                    .describe(content, r)
                                if (ratio.isNotEmpty()) LabeledRow("材料の比", ratio)
                                LabeledRow("器具", com.appathy.scienceroom.engine.Equipment.label(r.equipment))
                                LabeledRow("種類", r.changeType)
                                Spacer(Modifier.height(4.dp))
                                Text(r.explanation, fontSize = 13.sp)
                            } else {
                                Text(
                                    "実験で確かめると記録されます", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    val e = element
    if (e != null) {
        AlertDialog(
            onDismissRequest = { element = null },
            title = { Text("${e.symbol}　${e.name}") },
            text = {
                Column {
                    Thumb(e.imageId, 96)
                    Spacer(Modifier.height(8.dp))
                    LabeledRow("英語名", e.english)
                    LabeledRow("原子番号", e.atomicNumber.toString())
                    LabeledRow("分類", e.category)
                    if (e.group > 0) {
                        LabeledRow("周期表の位置", "第" + e.period + "周期・" + e.group + "族")
                    }
                    LabeledRow("常温での状態", e.state)
                    LabeledRow("融点", e.melting?.let { "$it ℃" } ?: "—")
                    LabeledRow("沸点", e.boiling?.let { "$it ℃" } ?: "—")
                    LabeledRow("危険度", dangerStars(e.danger))
                    Spacer(Modifier.height(6.dp))
                    Text(e.property, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("使われ方：${e.uses}", fontSize = 13.sp)
                    val holders = LinkEngine.materialsOf(content, e.id)
                    if (holders.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "この元素を含む素材：" + holders.joinToString("、") { it.name },
                            fontSize = 13.sp
                        )
                    }
                    val techs = LinkEngine.techOf(content, e.id)
                    if (techs.isNotEmpty()) {
                        Text(
                            "この知識が要る技術：" + techs.joinToString("、") { it.name },
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { element = null }) { Text("閉じる") } }
        )
    }

    val m = material
    if (m != null) {
        AlertDialog(
            onDismissRequest = { material = null },
            title = { Text(m.name) },
            text = {
                Column {
                    Thumb(m.imageId, 96)
                    Spacer(Modifier.height(8.dp))
                    LabeledRow("組成", m.composition)
                    LabeledRow("区分", m.kind)
                    LabeledRow("状態", if (m.natural) "自然界から採取" else "加工して得る")
                    LabeledRow("希少度", dangerStars(m.rarity))
                    LabeledRow("所持数", (state.inventory[m.id] ?: 0).toString())
                    Spacer(Modifier.height(6.dp))
                    Text("採取：${m.collection}", fontSize = 13.sp)
                    Text("加工：${m.processing}", fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(m.note, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "関連元素：" + m.elements.joinToString("、") { game.content.elementName(it) },
                        fontSize = 13.sp
                    )
                    fun label(id: String): String =
                        if (state.discoveredReactions.contains(id))
                            content.reactionById[id]?.name ?: id
                        else "???"

                    val madeBy = LinkEngine.madeBy(content, m.id)
                    if (madeBy.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("つくり方", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        madeBy.forEach { r ->
                            Text(
                                "・" + label(r.id) + "（" +
                                    r.inputs.joinToString("＋") { content.materialName(it) } + "）",
                                fontSize = 13.sp
                            )
                        }
                    }

                    val uses = LinkEngine.usedIn(content, m.id)
                    if (uses.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("使い道", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        uses.forEach { r ->
                            Text(
                                "・" + label(r.id) + " → " + content.materialName(r.product),
                                fontSize = 13.sp
                            )
                        }
                    }

                    val needed = LinkEngine.neededBy(content, m.id)
                    if (needed.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "必要とする技術：" + needed.joinToString("、") { it.name },
                            fontSize = 13.sp
                        )
                    }

                    val chain = LinkEngine.chainForward(content, m.id)
                    if (chain.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "この先につながるもの：" +
                                chain.joinToString("、") { content.materialName(it) },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { material = null }) { Text("閉じる") } }
        )
    }
}
