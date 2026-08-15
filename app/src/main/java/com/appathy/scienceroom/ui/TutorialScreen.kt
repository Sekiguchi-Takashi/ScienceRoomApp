package com.appathy.scienceroom.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.scienceroom.Feedback
import com.appathy.scienceroom.Game
import com.appathy.scienceroom.engine.Cast

private data class Page(
    val image: String,
    val wide: Boolean,
    val title: String,
    val body: String,
    val speaker: String = "akane"
)

private val pages = listOf(
    Page(
        "splash_logo", false,
        "ようこそ、科学室へ",
        "ここは何もない世界から科学を積み上げていく場所です。" +
            "元素を覚え、素材を探し、実験で確かめ、技術をひとつずつ解禁していきます。",
        "akane"
    ),
    Page(
        "loc_forest", true,
        "まず、探索する",
        "世界タブから地域を選んで探索すると、素材が手に入ります。" +
            "最初は森と川だけですが、技術が進むと山や海岸、洞窟へも行けるようになります。",
        "atsushi"
    ),
    Page(
        "eq_beaker", false,
        "次に、実験する",
        "実験タブで素材と分量、温度、時間、器具を決めて試します。" +
            "組み合わせが正しくても条件が合わなければ変化は起こりません。" +
            "失敗すると原因の候補が出るので、そこを手がかりに条件を変えていきます。",
        "mayumi"
    ),
    Page(
        "tech_kiln", false,
        "そして、技術を解禁する",
        "必要な知識と素材と実験がそろうと、技術タブで新しい技術を完成させられます。" +
            "技術は一本道ではなく枝分かれし、どれを先に伸ばすかで文明の色が変わります。",
        "shingo"
    ),
    Page(
        "elem_fe", false,
        "元素は繰り返して覚える",
        "クイズは10問で一区切りです。間違えたものは数分後にもう一度、" +
            "正解したものは1日後、3日後と間隔を空けて出てきます。" +
            "忘れかけたころに解き直すのが、いちばん記憶に残ります。",
        "rika"
    ),
    Page(
        "tech_fire", false,
        "最初の一歩は火起こし",
        "森で木を集め、技術タブで「火起こし」を完成させるところから始まります。" +
            "迷ったらホームの「おすすめの行動」を見てください。次にやることが出ています。",
        "toshiyuki"
    )
)

@Composable
fun TutorialScreen(game: Game, onFinish: () -> Unit) {
    var index by remember { mutableStateOf(0) }
    val page = pages[index]

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                game.update { it.copy(tutorialDone = true) }
                onFinish()
            }) { Text("スキップ") }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                if (page.wide) {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        AssetImage(
                            name = page.image,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Thumb(page.image, 150)
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    page.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                SpeechRow(Cast.byId(page.speaker), page.body, 72)
            }
        }

        Text(
            (index + 1).toString() + " / " + pages.size,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (index > 0) {
                TextButton(onClick = { index-- }) { Text("戻る") }
            }
            Button(
                onClick = {
                    game.feedback(Feedback.Kind.TAP)
                    if (index < pages.size - 1) {
                        index++
                    } else {
                        game.update { it.copy(tutorialDone = true) }
                        onFinish()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (index < pages.size - 1) "次へ" else "はじめる")
            }
        }
    }
}
