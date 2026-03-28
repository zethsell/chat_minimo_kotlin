package com.example.chat_minimo_kotlin.data.json

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive

/**
 * Normalização de IDs e flags booleanas heterogéneas vindas do Gson/JSON.
 */
object JsonIds {
    fun stringId(v: Any?): String? {
        if (v == null) return null
        return when (v) {
            is JsonNull -> null
            is JsonElement -> stringIdFromJson(v)
            is String -> v.trim().takeIf { it.isNotEmpty() }
            is Number -> {
                val d = v.toDouble()
                if (d % 1.0 == 0.0) d.toLong().toString() else v.toString().trim()
            }
            else -> v.toString().trim().takeIf { it.isNotEmpty() }
        }
    }

    fun stringIdFromJson(el: JsonElement?): String? {
        if (el == null || el is JsonNull) return null
        if (!el.isJsonPrimitive) return null
        val p = el as JsonPrimitive
        return when {
            p.isString -> p.asString.trim().takeIf { it.isNotEmpty() }
            p.isNumber -> {
                val d = p.asDouble
                if (d % 1.0 == 0.0) d.toLong().toString() else p.asString.trim()
            }
            else -> null
        }
    }

    fun deliveryTruthy(v: Any?): Boolean =
        when (v) {
            is JsonNull -> false
            is JsonElement -> deliveryTruthyFromJson(v)
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.equals("true", ignoreCase = true) || v == "1"
            else -> false
        }

    fun deliveryTruthyFromJson(el: JsonElement?): Boolean {
        if (el == null || el is JsonNull) return false
        if (!el.isJsonPrimitive) return false
        val p = el.asJsonPrimitive
        return when {
            p.isBoolean -> p.asBoolean
            p.isNumber -> p.asDouble != 0.0
            p.isString -> p.asString.equals("true", ignoreCase = true) || p.asString == "1"
            else -> false
        }
    }
}
