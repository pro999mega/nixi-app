package com.nagibatorz.nixi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Одна реплика диалога. */
data class Turn(val role: String, val content: String)

object Prefs {

    private const val FILE = "nixi"
    const val DEFAULT_TEXT = "google/gemma-4-31b-it:free"
    const val DEFAULT_VISION = "google/gemma-4-31b-it:free"

    const val DEFAULT_PERSONA =
        "Ты Никси — голосовой ассистент внутри телефона. Отвечай по-русски, дружелюбно и очень коротко: " +
            "одно-три предложения, без списков, без markdown и без эмодзи, потому что твой ответ читается вслух. " +
            "Если просят то, чего ты не умеешь, честно скажи об этом одной фразой."

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun apiKey(c: Context): String = sp(c).getString("apiKey", "").orEmpty()

    fun setApiKey(c: Context, value: String) {
        sp(c).edit().putString("apiKey", value.trim()).apply()
    }

    fun textModel(c: Context): String =
        sp(c).getString("textModel", DEFAULT_TEXT).orEmpty().ifBlank { DEFAULT_TEXT }

    fun visionModel(c: Context): String =
        sp(c).getString("visionModel", DEFAULT_VISION).orEmpty().ifBlank { DEFAULT_VISION }

    fun setModels(c: Context, text: String, vision: String) {
        sp(c).edit()
            .putString("textModel", text.trim().ifBlank { DEFAULT_TEXT })
            .putString("visionModel", vision.trim().ifBlank { DEFAULT_VISION })
            .apply()
    }

    /** Характер и правила общения ассистента. */
    fun persona(c: Context): String =
        sp(c).getString("persona", DEFAULT_PERSONA).orEmpty().ifBlank { DEFAULT_PERSONA }

    fun setPersona(c: Context, value: String) {
        sp(c).edit().putString("persona", value.trim().ifBlank { DEFAULT_PERSONA }).apply()
    }

    /** Слушать ли кодовое слово «Никси» в фоне. */
    fun wakeWord(c: Context): Boolean = sp(c).getBoolean("wakeWord", true)

    fun setWakeWord(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("wakeWord", value).apply()
    }

    fun history(c: Context): List<Turn> {
        val raw = sp(c).getString("history", "[]").orEmpty()
        val out = ArrayList<Turn>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val role = o.optString("role")
                val content = o.optString("content")
                if (role.isNotBlank() && content.isNotBlank()) out.add(Turn(role, content))
            }
        } catch (t: Throwable) {
            // битая история — просто начинаем заново
        }
        return out
    }

    fun addTurn(c: Context, role: String, content: String) {
        if (content.isBlank()) return
        val list = ArrayList(history(c))
        list.add(Turn(role, content.take(1500)))
        while (list.size > 14) list.removeAt(0)
        val arr = JSONArray()
        for (t in list) {
            arr.put(JSONObject().put("role", t.role).put("content", t.content))
        }
        sp(c).edit().putString("history", arr.toString()).apply()
    }

    fun clearHistory(c: Context) {
        sp(c).edit().putString("history", "[]").apply()
    }
}
