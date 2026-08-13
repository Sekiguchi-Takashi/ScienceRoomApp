package com.appathy.scienceroom.data

import kotlinx.serialization.Serializable

@Serializable
data class Element(
    val id: String,
    val atomicNumber: Int,
    val symbol: String,
    val name: String,
    val english: String,
    val category: String,
    val state: String,
    val melting: Double? = null,
    val boiling: Double? = null,
    val color: String,
    val locations: List<String> = emptyList(),
    val materials: List<String> = emptyList(),
    val uses: String,
    val property: String,
    val danger: Int,
    val imageId: String,
    val group: Int = 0,
    val period: Int = 0
)

@Serializable
data class GameMaterial(
    val id: String,
    val name: String,
    val imageId: String,
    val composition: String,
    val elements: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val collection: String,
    val processing: String,
    val rarity: Int,
    val natural: Boolean,
    val kind: String = "化合物",
    val note: String
)

@Serializable
data class GameLocation(
    val id: String,
    val name: String,
    val imageId: String,
    val environment: String,
    val materials: List<String> = emptyList(),
    val difficulty: Int,
    val danger: Int,
    val unlockedBy: String? = null,
    val description: String
)

@Serializable
data class Technology(
    val id: String,
    val name: String,
    val imageId: String,
    val description: String,
    val requiredTech: List<String> = emptyList(),
    val requiredMaterials: List<String> = emptyList(),
    val requiredElements: List<String> = emptyList(),
    val requiredReactions: List<String> = emptyList(),
    val unlocksLocations: List<String> = emptyList(),
    val unlocksMaterials: List<String> = emptyList(),
    val difficulty: Int
)

@Serializable
data class Reaction(
    val id: String,
    val name: String,
    val inputs: List<String> = emptyList(),
    val minTemp: Int,
    val maxTemp: Int,
    val minDuration: Int,
    val equipment: String? = null,
    val ratios: Map<String, Int>? = null,
    val product: String,
    val observation: String,
    val explanation: String,
    val principle: String,
    val changeType: String,
    val danger: Int,
    val simulationOnly: Boolean = false,
    val exp: Int = 10
)

@Serializable
data class TempRange(
    val min: Int,
    val max: Int,
    val state: String,
    val change: String,
    val visible: String,
    val danger: Int,
    val warning: String = ""
)

@Serializable
data class TempBehavior(
    val id: String,
    val name: String,
    val imageId: String,
    val ranges: List<TempRange> = emptyList()
)

/** 元素ごとの学習状態。mode は sym2name / name2sym / image / property */
@Serializable
data class ElementLearning(
    val correct: Map<String, Int> = emptyMap(),
    val total: Map<String, Int> = emptyMap(),
    val lastReviewed: Long = 0L,
    val nextReview: Long = 0L,
    val streak: Int = 0
) {
    fun accuracy(mode: String): Double {
        val t = total[mode] ?: 0
        if (t == 0) return -1.0
        return (correct[mode] ?: 0).toDouble() / t
    }

    fun answered(): Int = total.values.sum()

    /** 忘却リスク 0.0〜1.0。未学習は 1.0 */
    fun forgettingRisk(now: Long): Double {
        if (answered() == 0) return 1.0
        val acc = total.keys.map { accuracy(it) }.filter { it >= 0 }.average()
        val overdue = if (nextReview == 0L) 1.0
        else ((now - nextReview).toDouble() / (1000.0 * 60 * 60 * 24)).coerceIn(-1.0, 3.0)
        return ((1.0 - acc) * 0.7 + (overdue.coerceAtLeast(0.0) / 3.0) * 0.3).coerceIn(0.0, 1.0)
    }
}

/** 実験ノートの1件。入力条件をすべて残すので同じ結果を再現できる */
@Serializable
data class ExperimentLog(
    val time: Long,
    val materials: List<String> = emptyList(),
    val quantities: Map<String, Int> = emptyMap(),
    val temperature: Int = 0,
    val duration: Int = 1,
    val equipment: String? = null,
    val rank: String = "C",
    val title: String = "",
    val reactionId: String? = null,
    val productId: String? = null,
    val causes: List<String> = emptyList(),
    val ruleVersion: String = "1"
)

/** 今日のミッションの基準値。日付が変わったら現在値で置き直す */
@Serializable
data class DailyProgress(
    val date: String = "",
    val quizCorrectBase: Int = 0,
    val quizCountBase: Int = 0,
    val exploreBase: Int = 0,
    val experimentBase: Int = 0,
    val successBase: Int = 0,
    val knownElementsBase: Int = 0,
    val materialsBase: Int = 0
)

/** 1日分の活動量。日付が変わったときに確定させる */
@Serializable
data class DailyStat(
    val date: String,
    val quizAnswered: Int = 0,
    val quizCorrect: Int = 0,
    val experiments: Int = 0,
    val successes: Int = 0,
    val explores: Int = 0,
    val newElements: Int = 0,
    val newMaterials: Int = 0
) {
    fun accuracy(): Int =
        if (quizAnswered == 0) 0 else (quizCorrect * 100 / quizAnswered).coerceIn(0, 100)
    fun activity(): Int = quizAnswered + experiments + explores
    fun isEmpty(): Boolean = activity() == 0 && newElements == 0 && newMaterials == 0
}

@Serializable
data class PlayerState(
    val exp: Int = 0,
    val inventory: Map<String, Int> = emptyMap(),
    val discoveredMaterials: Set<String> = emptySet(),
    val knownElements: Set<String> = emptySet(),
    val discoveredReactions: Set<String> = emptySet(),
    val completedTech: Set<String> = emptySet(),
    val unlockedLocations: Set<String> = setOf("forest", "river"),
    val learning: Map<String, ElementLearning> = emptyMap(),
    val currentGoal: String? = null,
    val experimentCount: Int = 0,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val exploreCount: Int = 0,
    val quizCount: Int = 0,
    val quizCorrect: Int = 0,
    val hintLevel: Int = 2,
    val autoHint: Boolean = true,
    val researchLeads: Set<String> = emptySet(),
    val notebook: List<ExperimentLog> = emptyList(),
    val favorites: List<ExperimentLog> = emptyList(),
    val daily: DailyProgress = DailyProgress(),
    val history: List<DailyStat> = emptyList(),
    val eventWeek: Long = -1,
    val eventCount: Int = 0,
    val eventClaimed: Boolean = false,
    val clearedEvents: Int = 0,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val soundOn: Boolean = true,
    val hapticOn: Boolean = true,
    val bgmOn: Boolean = false,
    val tutorialDone: Boolean = false,
    val lastFailureIds: List<String> = emptyList()
) {
    val level: Int get() = 1 + exp / 100
    val expInLevel: Int get() = exp % 100

    fun has(materialId: String, count: Int = 1): Boolean = (inventory[materialId] ?: 0) >= count

    fun successRate(): Int =
        if (experimentCount == 0) 0 else (successCount * 100 / experimentCount)
}

/** 実験の入力条件 */
data class ExperimentInput(
    val materials: List<String>,
    val quantities: Map<String, Int> = emptyMap(),
    val temperature: Int,
    val duration: Int,
    val equipment: String?
) {
    fun amount(id: String): Int = quantities[id] ?: 1
}

enum class Rank { S, A, B, C, D }

data class FailureCause(val label: String, val weight: String)

data class ExperimentResult(
    val rank: Rank,
    val reaction: Reaction?,
    val productId: String?,
    val title: String,
    val observation: String,
    val explanation: String,
    val principle: String,
    val causes: List<FailureCause>,
    val hint: String,
    val gainedExp: Int,
    val newKnowledge: List<String>,
    val warning: String?,
    val leadReactionId: String? = null
)
