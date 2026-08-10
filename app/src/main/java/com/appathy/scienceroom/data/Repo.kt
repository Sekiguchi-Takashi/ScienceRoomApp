package com.appathy.scienceroom.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

class Content(
    val elements: List<Element>,
    val materials: List<GameMaterial>,
    val locations: List<GameLocation>,
    val technologies: List<Technology>,
    val reactions: List<Reaction>,
    val temperatures: List<TempBehavior>
) {
    val elementById: Map<String, Element> = elements.associateBy { it.id }
    val materialById: Map<String, GameMaterial> = materials.associateBy { it.id }
    val locationById: Map<String, GameLocation> = locations.associateBy { it.id }
    val techById: Map<String, Technology> = technologies.associateBy { it.id }
    val reactionById: Map<String, Reaction> = reactions.associateBy { it.id }

    fun elementName(id: String): String = elementById[id]?.name ?: id
    fun materialName(id: String): String = materialById[id]?.name ?: id
    fun techName(id: String): String = techById[id]?.name ?: id

    companion object {
        private fun read(context: Context, name: String): String =
            context.assets.open("data/$name").bufferedReader().use { it.readText() }

        fun load(context: Context): Content = Content(
            elements = json.decodeFromString(read(context, "elements.json")),
            materials = json.decodeFromString(read(context, "materials.json")),
            locations = json.decodeFromString(read(context, "locations.json")),
            technologies = json.decodeFromString(read(context, "technologies.json")),
            reactions = json.decodeFromString(read(context, "reactions.json")),
            temperatures = json.decodeFromString(read(context, "temperature.json"))
        )
    }
}

object PlayerRepo {
    private const val PREF = "scienceroom"
    private const val KEY = "player_state"

    fun load(context: Context): PlayerState {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return PlayerState()
        return try {
            json.decodeFromString<PlayerState>(raw)
        } catch (e: Exception) {
            PlayerState()
        }
    }

    fun save(context: Context, state: PlayerState) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.encodeToString(state))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
