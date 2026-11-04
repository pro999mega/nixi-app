package com.nagibatorz.nixi

import android.content.Context

object Prefs {

    private const val FILE = "nixi"
    const val DEFAULT_TEXT = "meta-llama/llama-3.3-70b-instruct:free"
    const val DEFAULT_VISION = "google/gemini-2.0-flash-exp:free"

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
}
