package com.appathy.scienceroom.engine

import com.appathy.scienceroom.data.Content
import com.appathy.scienceroom.data.PlayerState
import com.appathy.scienceroom.data.FailureCause
import com.appathy.scienceroom.data.Reaction

/**
 * 実験のとっつきにくさを下げるための手引き。
 * いきなり答えを出さず、4段階に分けて少しずつ開いていく。
 */
enum class Assist(val id: String, val label: String, val note: String) {
    SELF("self", "自分で試す", "手引きを出さない。手さぐりで進める"),
    GUIDED("guided", "ヒントつき", "使える温度の目安と、次に挑む実験を教えてもらう"),
    STEP("step", "手順を見る", "材料・器具・温度をすべて確かめてから始める")
}

/** 手引きの1段。step は 0 から 3 まで */
data class Lesson(
    val reaction: Reaction,
    val step: Int,
    val title: String,
    val body: String,
    val speaker: Character
) {
    val isLast: Boolean get() = step >= 3
}

object TeacherEngine {

    /** 教える題材を選ぶ。いま手が届くものを優先し、無ければ一番やさしいものを指す */
    fun nextLesson(content: Content, state: PlayerState): Reaction? {
        val undone = content.reactions.filter { !state.discoveredReactions.contains(it.id) }
        if (undone.isEmpty()) return null

        val reachable = undone.filter { r ->
            r.inputs.all { state.has(it) } && Equipment.satisfies(
                Equipment.available(state).lastOrNull(), r.equipment
            )
        }
        if (reachable.isNotEmpty()) return reachable.minByOrNull { it.inputs.size }

        val hasMaterials = undone.filter { r -> r.inputs.all { state.has(it) } }
        if (hasMaterials.isNotEmpty()) return hasMaterials.minByOrNull { it.inputs.size }

        return undone.minByOrNull { it.minDuration + it.inputs.size }
    }

    fun lesson(content: Content, reaction: Reaction, step: Int): Lesson = when (step) {
        0 -> Lesson(
            reaction, 0,
            "何をつくるか",
            "今回の目標は「" + content.materialName(reaction.product) + "」だ。" +
                reaction.principle + "がはたらく。まずはそれだけ頭に入れておこう。",
            Cast.daichi
        )
        1 -> Lesson(
            reaction, 1,
            "材料をそろえる",
            "必要なのは " +
                reaction.inputs.joinToString("、") { content.materialName(it) } + "。" +
                (if (reaction.ratios.isNullOrEmpty()) "分量は気にしなくていい。"
                else "入れる比が大事で、" + RatioEngine.describe(content, reaction) + " にする。") +
                "足りなければ探索で集めてこよう。",
            Cast.yuto
        )
        2 -> Lesson(
            reaction, 2,
            "器具を決める",
            (if (reaction.equipment == null) "特別な器具はいらない。そのまま混ぜられる。"
            else "「" + Equipment.label(reaction.equipment) + "」が要る。" +
                "器具によって出せる温度の上限が決まっていて、" +
                Equipment.label(reaction.equipment) + "なら " +
                Equipment.maxTemp(reaction.equipment) + "℃ まで上げられる。"),
            Cast.daichi
        )
        else -> Lesson(
            reaction, 3,
            "温度と時間",
            reaction.minTemp.toString() + "〜" + reaction.maxTemp + "℃ で、時間は " +
                reaction.minDuration + " 以上。範囲の真ん中あたりで、少し長めにかけると仕上がりがいい。" +
                "ここまで分かれば、あとは動かすだけだ。",
            Cast.rinko
        )
    }

    /** 選んだ素材に対応する反応。温度の目安を出すために使う */
    fun matching(content: Content, selected: List<String>): Reaction? {
        if (selected.isEmpty()) return null
        val sorted = selected.sorted()
        return content.reactions.firstOrNull { it.inputs.sorted() == sorted }
    }

    /** ヒントつき以上のときに温度スライダーへ添える文 */
    fun temperatureAdvice(
        content: Content,
        assist: Assist,
        selected: List<String>,
        current: Int
    ): String {
        if (assist == Assist.SELF) return ""
        val r = matching(content, selected) ?: return ""

        if (assist == Assist.STEP) {
            return "この組み合わせは " + r.minTemp + "〜" + r.maxTemp + "℃／時間 " +
                r.minDuration + " 以上／" + Equipment.label(r.equipment)
        }

        return when {
            current < r.minTemp - 200 -> "まだかなり低い。もっと上げてみよう"
            current < r.minTemp -> "もう少しで届きそうだ"
            current > r.maxTemp + 200 -> "上げすぎている。かなり下げよう"
            current > r.maxTemp -> "少し高い。下げてみよう"
            else -> "この温度なら変化が起きそうだ"
        }
    }

    /** 失敗したときに、次に何を変えればいいかを一言で */
    fun advice(causes: List<FailureCause>): String {
        if (causes.isEmpty()) return ""
        return when (causes.first().label) {
            "温度条件" -> "変えるのは温度だけにしよう。一度に二つ動かすと、どちらが効いたか分からなくなる"
            "加熱時間" -> "温度はそのままで、時間だけ延ばしてみよう"
            "器具" -> "器具を変えれば届くはずだ。ほかの条件はさわらないでいい"
            "材料の比" -> "量のつり合いだけを変えてみよう。総量は増やしても減らしても同じだよ"
            "材料の組み合わせ" -> "組み合わせから見直そう。図鑑で素材のつながりを辿ると早い"
            "素材の所持数" -> "まず探索で集めてこよう"
            else -> "ひとつずつ確かめれば必ず分かる"
        }
    }
}
