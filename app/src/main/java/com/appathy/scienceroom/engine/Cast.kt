package com.appathy.scienceroom.engine

import com.appathy.scienceroom.data.PlayerState
import kotlin.random.Random

/**
 * 科学室の6人。役割ごとに口ぶりを変え、どの画面で何が起きているかを言葉で補う。
 * セリフはルールで選ぶだけで、AI には投げない。
 */
data class Character(
    val id: String,
    val name: String,
    val imageId: String,
    val role: String,
    val trait: String
)

enum class Scene {
    HOME, EXPLORE_FOUND, EXPLORE_MISS, EXPERIMENT_SUCCESS, EXPERIMENT_PARTIAL,
    EXPERIMENT_FAIL, DISCOVER_LEAD, QUIZ_CORRECT, QUIZ_WRONG, TECH_UNLOCK, EVENT
}

object Cast {

    val atsushi = Character(
        "atsushi", "篤史", "char_atsushi",
        "探索の相棒", "とにかく外へ出たがる。失敗しても次を見つけてくる"
    )
    val mayumi = Character(
        "mayumi", "真由美", "char_mayumi",
        "実験の記録係", "条件を几帳面に書き留める。失敗の原因を落ち着いて並べる"
    )
    val rika = Character(
        "rika", "梨花", "char_rika",
        "元素の案内役", "覚え方のこつを教えてくれる。忘れても責めない"
    )
    val shingo = Character(
        "shingo", "慎吾", "char_shingo",
        "技術の職人", "手を動かして確かめる。できあがると誰より喜ぶ"
    )
    val toshiyuki = Character(
        "toshiyuki", "敏行", "char_toshiyuki",
        "理屈の解説役", "なぜそうなるかを短く言う。理屈が通ると満足する"
    )
    val akane = Character(
        "akane", "茜", "char_akane",
        "計画の立案役", "次に何をすべきかを整理する。回り道も否定しない"
    )

    val rinko = Character(
        "rinko", "凛子先生", "char_rinko",
        "化学の先生", "何が起きているかを順序立てて説明する。急かさない"
    )
    val daichi = Character(
        "daichi", "大地先生", "char_daichi",
        "実験の先生", "手順を一段ずつ示す。まず作ってみよう、が口ぐせ"
    )
    val yuto = Character(
        "yuto", "悠斗", "char_yuto",
        "一学年上の先輩", "つまずいた場所を覚えている。近い目線で助言する"
    )

    val students = listOf(atsushi, mayumi, rika, shingo, toshiyuki, akane)
    val teachers = listOf(daichi, rinko, yuto)
    val all = students + teachers

    fun byId(id: String): Character = all.firstOrNull { it.id == id } ?: atsushi

    /** 場面に合う人を選ぶ */
    fun speaker(scene: Scene): Character = when (scene) {
        Scene.HOME, Scene.EVENT -> akane
        Scene.EXPLORE_FOUND, Scene.EXPLORE_MISS -> atsushi
        Scene.EXPERIMENT_SUCCESS, Scene.EXPERIMENT_PARTIAL, Scene.EXPERIMENT_FAIL -> mayumi
        Scene.DISCOVER_LEAD -> toshiyuki
        Scene.QUIZ_CORRECT, Scene.QUIZ_WRONG -> rika
        Scene.TECH_UNLOCK -> shingo
    }

    private val lines: Map<Scene, List<String>> = mapOf(
        Scene.HOME to listOf(
            "次の一手を整理しておいたよ。上から順でいい",
            "急がなくていい。ひとつ進めば道はつながる",
            "迷ったら、いま持っている素材を見返してみて"
        ),
        Scene.EXPLORE_FOUND to listOf(
            "見つけた！ これ、使えると思う",
            "やっぱり来てよかった。持って帰ろう",
            "足元をよく見ると、こういうのが落ちてるんだ"
        ),
        Scene.EXPLORE_MISS to listOf(
            "今日は空振りか。でも場所は覚えた",
            "何も無い日もある。もう一回まわってみよう",
            "こういうときこそ、別の場所を試すのもありだよ"
        ),
        Scene.EXPERIMENT_SUCCESS to listOf(
            "できた。条件をそのまま書き留めておくね",
            "狙いどおり。この温度と時間は覚えておこう",
            "きれいに変わった。ノートに残しておくよ"
        ),
        Scene.EXPERIMENT_PARTIAL to listOf(
            "惜しい。あと一歩どこかが足りてない",
            "変化のきざしはあった。条件を少し動かしてみよう",
            "材料は合ってる。あとは条件を詰めるだけ"
        ),
        Scene.EXPERIMENT_FAIL to listOf(
            "だめだったね。でも、だめだった条件が分かった",
            "失敗も記録。次に同じ道を通らずに済む",
            "原因の候補を並べておいたよ。上から疑ってみて"
        ),
        Scene.DISCOVER_LEAD to listOf(
            "待って、いまの変化はおかしい。何か起きてる",
            "狙ってなかったのに反応した。これは調べる価値がある",
            "偶然だけど、偶然で片づけるには惜しい"
        ),
        Scene.QUIZ_CORRECT to listOf(
            "正解。その調子でいこう",
            "覚えてるね。次はもう少し間を空けて出すよ",
            "うん、ばっちり"
        ),
        Scene.QUIZ_WRONG to listOf(
            "惜しい。この元素は何に使われるかで覚えると楽だよ",
            "だいじょうぶ、すぐまた出てくるから",
            "間違えたところは、いちばん伸びるところ"
        ),
        Scene.TECH_UNLOCK to listOf(
            "できたぞ！ これで作れるものが増える",
            "手が届いた。次の技術も見えてきたな",
            "積み上げたぶんが形になったな"
        ),
        Scene.EVENT to listOf(
            "今週はこれが狙い目。まとめて進めよう",
            "せっかくの週だから、少しだけ寄り道してみない？",
            "期限は決まってるけど、無理はしなくていい"
        )
    )

    /** seed を渡すと同じ場面で同じセリフになる */
    fun line(scene: Scene, seed: Int): String {
        val list = lines[scene] ?: return ""
        return list[Random(seed).nextInt(list.size)]
    }

    /** 進み具合に応じて、いま前に出る人を決める */
    fun featured(state: PlayerState): Character = when {
        state.completedTech.size >= 15 -> shingo
        state.knownElements.size >= 15 -> rika
        state.experimentCount >= 20 -> mayumi
        state.exploreCount >= 15 -> atsushi
        state.discoveredReactions.size >= 8 -> toshiyuki
        else -> akane
    }
}
