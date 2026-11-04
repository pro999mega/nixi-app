package com.nagibatorz.nixi

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private val BG = 0xFF0B0B0D.toInt()
    private val CARD = 0xFF15151B.toInt()
    private val LINE = 0xFF25252E.toInt()
    private val ACC = 0xFFFF7A18.toInt()
    private val TXT = 0xFFEDEDF1.toInt()
    private val MUT = 0xFF8B8B95.toInt()
    private val ONA = 0xFF101014.toInt()

    private val h = Handler(Looper.getMainLooper())
    private lateinit var box: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(BG)
        setContentView(col)

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(14), dp(16), dp(10))
        col.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val title = TextView(this)
        title.text = "Никси"
        title.textSize = 24f
        title.setTextColor(ACC)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val sett = TextView(this)
        sett.text = "⚙"
        sett.textSize = 20f
        sett.setTextColor(TXT)
        sett.gravity = Gravity.CENTER
        sett.background = round(CARD, 14)
        sett.setOnClickListener { openSettings() }
        head.addView(sett, LinearLayout.LayoutParams(dp(44), dp(44)))

        scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(6), dp(12), dp(6))
        scroll.addView(box, ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(8), dp(12), dp(14))
        col.addView(row)

        input = EditText(this)
        input.hint = "Сообщение…"
        input.setTextColor(TXT)
        input.setHintTextColor(MUT)
        input.textSize = 15f
        input.maxLines = 4
        input.background = round(CARD, 22, 1, LINE)
        input.setPadding(dp(16), dp(10), dp(16), dp(10))
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, a, _ ->
            if (a == EditorInfo.IME_ACTION_SEND) { send(input.text.toString()); true } else false
        }
        row.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val send = TextView(this)
        send.text = "➤"
        send.textSize = 20f
        send.setTextColor(ONA)
        send.gravity = Gravity.CENTER
        send.background = round(ACC, 20)
        send.setOnClickListener { send(input.text.toString()) }
        val slp = LinearLayout.LayoutParams(dp(48), dp(48))
        slp.leftMargin = dp(8)
        row.addView(send, slp)

        greet()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun round(color: Int, r: Int, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(color)
        d.cornerRadius = dp(r).toFloat()
        if (stroke > 0) d.setStroke(dp(stroke), strokeColor)
        return d
    }

    private fun bubble(text: String, mine: Boolean): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 15f
        tv.setTextColor(if (mine) ONA else TXT)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.background = round(if (mine) ACC else CARD, 18)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = if (mine) Gravity.END else Gravity.START
        lp.topMargin = dp(6)
        tv.layoutParams = lp
        box.addView(tv)
        scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
        return tv
    }

    private fun greet() {
        bubble("Привет! Я Никси. Спроси что угодно — отвечу голосом и текстом.", false)
    }

    private fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        bubble(text, true)
        input.setText("")

        val typing = bubble("печатает…", false)

        Thread {
            val key = Prefs.apiKey(this)
            val answer = if (key.isBlank()) {
                "Ключ OpenRouter не вписан. Нажми ⚙ и добавь его."
            } else {
                val arr = JSONArray()
                arr.put(JSONObject().put("role", "system").put("content", Prefs.persona(this)))
                for (t in Prefs.history(this)) {
                    arr.put(JSONObject().put("role", t.role).put("content", t.content))
                }
                arr.put(JSONObject().put("role", "user").put("content", text))
                AiClient.chat(key, Prefs.textModel(this), arr)
            }
            h.post {
                typing.text = answer
                Prefs.addTurn(this, "user", text)
                Prefs.addTurn(this, "assistant", answer)
                scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }.start()
    }

    private fun openSettings() {
        val key = EditText(this)
        key.hint = "sk-or-v1-…"
        key.setText(Prefs.apiKey(this))
        key.setTextColor(TXT)
        key.setHintTextColor(MUT)

        val persona = EditText(this)
        persona.hint = "Характер"
        persona.setText(Prefs.persona(this))
        persona.setTextColor(TXT)
        persona.setHintTextColor(MUT)

        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(dp(20), dp(12), dp(20), dp(4))
        wrap.addView(key)
        wrap.addView(persona)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setView(wrap)
            .setPositiveButton("Сохранить") { _, _ ->
                Prefs.setApiKey(this, key.text.toString())
                Prefs.setPersona(this, persona.text.toString())
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
