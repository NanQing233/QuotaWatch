package io.apimeter.wear.providers

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.asObjectOrNull(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()

internal fun JsonElement?.asArrayOrNull(): JsonArray? = runCatching { this?.jsonArray }.getOrNull()

internal fun JsonObject.text(name: String): String? =
    this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

internal fun JsonObject.longOrZero(name: String): Long =
    this[name]?.jsonPrimitive?.longOrNull ?: 0L

internal fun JsonObject.doubleOrZero(name: String): Double =
    this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0

internal fun JsonObject.boolOrFalse(name: String): Boolean =
    this[name]?.jsonPrimitive?.booleanOrNull ?: false

internal fun JsonObject.fieldIgnoreCase(name: String): JsonElement? =
    this[name] ?: entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
