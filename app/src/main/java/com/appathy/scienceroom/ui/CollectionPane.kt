package com.appathy.scienceroom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Feedback
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.data.GameMaterial

private const val SLOTS = 6
private val RareGold = Color(0xFFC79A3A)

/** 集めた素材をカードにして、部屋の棚に並べる */
@Composable
fun CollectionPane(game: Game) {
    val content = game.content
    val state = game.state
    var picking by remember { mutableStateOf(-1) }
    var detail by remember { mutableStateOf<GameMaterial?>(null) }

    val found = content.materials.filter { state.discoveredMaterials.contains(it.id) }
    val slots = List(SLOTS) { i -> state.displayCase.getOrNull(i) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("かざり棚", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "見つけた素材をカードにして飾れます。空いた枠を押すと選べます",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                AssetImage(
                    name = "room_home",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color(0x33FFFFFF)))
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (rowIndex in 0 until 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0 until 3) {
                                val index = rowIndex * 3 + col
                                val id = slots.getOrNull(index)
                                DisplaySlot(
                                    material = if (id.isNullOrEmpty()) null
                                    else content.materialById[id],
                                    onClick = {
                                        game.feedback(Feedback.Kind.TAP)
                                        picking = index
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle(
                "コレクション　" + found.size + " / " + content.materials.size
            )
        }

        items(content.materials.chunked(4)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { m ->
                    val owned = state.discoveredMaterials.contains(m.id)
                    Box(modifier = Modifier.weight(1f)) {
                        CollectionCard(
                            material = m,
                            found = owned,
                            count = state.inventory[m.id] ?: 0,
                            onClick = { if (owned) detail = m }
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    if (picking >= 0) {
        AlertDialog(
            onDismissRequest = { picking = -1 },
            title = { Text("飾るものを選ぶ") },
            text = {
                if (found.isEmpty()) {
                    Text("まだ飾れる素材がありません。世界タブから探してみましょう", fontSize = 14.sp)
                } else {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(found) { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = state.displayCase.toMutableList()
                                        while (next.size < SLOTS) next.add("")
                                        next[picking] = m.id
                                        game.update { it.copy(displayCase = next.toList()) }
                                        game.feedback(Feedback.Kind.DISCOVER)
                                        picking = -1
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Thumb(m.imageId, 40)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(m.name, fontSize = 14.sp)
                                    Text(
                                        m.composition + "　希少度 " + dangerStars(m.rarity),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = state.displayCase.toMutableList()
                    while (next.size < SLOTS) next.add("")
                    next[picking] = ""
                    game.update { it.copy(displayCase = next.toList()) }
                    picking = -1
                }) { Text("この枠を空にする") }
            },
            dismissButton = { TextButton(onClick = { picking = -1 }) { Text("閉じる") } }
        )
    }

    val d = detail
    if (d != null) {
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(d.name) },
            text = {
                Column {
                    Pop(key = d) { Thumb(d.imageId, 110) }
                    Spacer(Modifier.height(8.dp))
                    LabeledRow("組成", d.composition)
                    LabeledRow("区分", d.kind)
                    LabeledRow("希少度", dangerStars(d.rarity))
                    LabeledRow("所持数", (state.inventory[d.id] ?: 0).toString())
                    Spacer(Modifier.height(6.dp))
                    Text(d.note, fontSize = 14.sp)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("閉じる") } }
        )
    }
}

@Composable
private fun DisplaySlot(material: GameMaterial?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(74.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xE6FFFBF4))
            .border(
                width = if (material != null && material.rarity >= 4) 2.dp else 1.dp,
                color = if (material != null && material.rarity >= 4) RareGold
                else Color(0x33000000),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (material == null) {
            Text("＋", fontSize = 20.sp, color = Color(0x66000000))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Thumb(material.imageId, 40)
                Text(
                    material.name,
                    fontSize = 9.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CollectionCard(
    material: GameMaterial,
    found: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    val rare = material.rarity >= 4
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (found) MaterialTheme.colorScheme.surface else Color(0x11000000)
            )
            .border(
                width = if (found && rare) 2.dp else 1.dp,
                color = if (found && rare) RareGold else Color(0x22000000),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (found) {
            Thumb(material.imageId, 46)
            Text(material.name, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
            Text(
                "×" + count,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Box(
                modifier = Modifier.height(46.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("？", fontSize = 22.sp, modifier = Modifier.alpha(0.4f))
            }
            Text("未発見", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" ", fontSize = 9.sp)
        }
    }
}
