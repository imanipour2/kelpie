package com.kelpie.browser.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** HTTP JSON conversion shared by admission and response serialization. */
internal object HttpJson {
    val codec = Json { ignoreUnknownKeys = true }

    fun parseJsonBody(text: String): Map<String, Any?>? {
        if (text.isEmpty()) return emptyMap()
        return try {
            val element = codec.parseToJsonElement(text)
            jsonObjectToMap(element.jsonObject)
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> = obj.entries.associate { (k, v) -> k to jsonElementToAny(v) }

    private fun jsonElementToAny(element: JsonElement): Any? =
        when (element) {
            is JsonNull -> null
            is JsonPrimitive ->
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            is JsonObject -> jsonObjectToMap(element)
            is JsonArray -> element.map { jsonElementToAny(it) }
        }

    fun mapToJsonString(map: Map<String, Any?>): String = codec.encodeToString(JsonElement.serializer(), mapToJsonElement(map))

    private fun mapToJsonElement(map: Map<String, Any?>): JsonElement = JsonObject(map.entries.associate { (k, v) -> k to anyToJsonElement(v) })

    @Suppress("UNCHECKED_CAST")
    private fun anyToJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> mapToJsonElement(value as Map<String, Any?>)
            is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
}
