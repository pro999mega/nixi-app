package com.nagibatorz.nixi

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var etInput: EditText
    private lateinit var drawer: LinearLayout
    private lateinit var scrim: View
    private lateinit var drawerHistory: LinearLayout
    private lateinit var tvCapsuleToggle: TextView
    private lateinit var tvSub: TextView
    private lateinit var statusDot: View

    private val ui = Handler(Looper.getMainLooper())
    private var chatId: String = ""
    private var busy = false
    private var drawerOpen = false

    private val ru = Locale.forLanguageTag("ru")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatList = findViewById(R.id.chatList)
        chatScroll = findViewById(R.id.chatScroll)
        etInput = findViewById(R.id.etInput)
        drawer = findViewById(R.id.drawer)
        scrim = findViewById(R.id.scrim)
        drawerHistory = findViewById(R.id.drawerHistory)
        tvCapsuleToggle = findViewById(R.id.btnCapsuleToggle)
        tvSub = findViewById(R.id.tvSub)
        statusDot = findViewById(R.id.viewStatusDot)

        decorate()

        findViewById<View>(R.id.btnMenu).setOnClickListener { openDrawer() }
        scrim.setOnClickListener { closeDrawer() }
        findViewById<View>(R.id.btnNewChatTop).setOnClickListener { startNewChat() }
        findViewById<View>(R.id.btnNewChat).setOnClickListener {
            startNewChat()
            closeDrawer()
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnCapsuleToggle).setOnClickListener { toggleCapsule() }
        findViewById<View>(R.id.btnSend).setOnClickListener { send() }

        findViewById<View>(R.id.btnVoice).setOnClickListener { startVoice() }
        findViewById<View>(R.id.chipScreen).setOnClickListener { quick("Что у меня на экране?") }
        findViewById<View>(R.id.chipTimer).setOnClickListener { quick("Поставь таймер на 10 минут") }
        findViewById<View>(R.id.chipTime).setOnClickListener { quick("Который час?") }

        chatId = Prefs.currentChat(this)
        if (chatId.isBlank()) {
            chatId = Prefs.newChatId()
            Prefs.setCurrentChat(this, chatId)
        }

        renderCurrentChat()
        refreshDrawer()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshDrawer()
    }

    // ---------------- оформление ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
            if (stroke > 0) setStroke(stroke, strokeColor)
        }

    private fun decorate() {
        drawer.translationX = -dp(304).toFloat()

        findViewById<View>(R.id.inputBar).background =
            round(0xFF17171E.toInt(), dp(24).toFloat(), 1, 0x1AFFFFFF)

        val inputRound = round(0xFF1C1C24.toInt(), dp(20).toFloat())
        findViewById<View>(R.id.btnVoice).background = inputRound.copy().apply { setColor(0xFF1C1C24.toInt()) }
        findViewById<View>(R.id.btnSend).background = round(0xFFFF7A18.toInt(), dp(20).toFloat())

        val chipBg = round(0xFF16161D.toInt(), dp(18).toFloat(), 1, 0x14FFFFFF)
        findViewById<View>(R.id.chipScreen).background = chipBg
        findViewById<View>(R.id.chipTimer).background = chipBg.copy().apply { setColor(0xFF16161D.toInt()) }
        findViewById<View>(R.id.chipTime).background = chipBg.copy().apply { setColor(0xFF16161D.toInt()) }

        findViewById<View>(R.id.btnNewChat).background = round(0xFF1C1C24.toInt(), dp(16).toFloat())

        (statusDot.background as? GradientDrawable)?.cornerRadius = dp(3).toFloat()
            ?: run { statusDot.background = round(0xFFFF7A18.toInt(), dp(3).toFloat()) }
    }

    private fun updateStatus() {
        val key = Prefs.apiKey(this).isNotBlank()
        val capsule = CapsuleService.running
        tvSub.text = when {
            !key -> "нужен ключ OpenRouter"
            capsule -> "капсула работает"
            else -> "на связи"
        }
        statusDot.background = round(
            if (key) 0xFFFF7A18.toInt() else 0xFF7A7A85.toInt(), dp(3).toFloat()
        )
        tvCapsuleToggle.text = if (capsule) "◉   Остановить капсулу" else "◉   Включить капсулу"
    }

    // ---------------- чат ----------------

    private fun renderCurrentChat() {
        chatList.removeAllViews()
        val chat = Prefs.chatById(this, chatId)
        val messages = chat?.messages.orEmpty()

        if (messages.isEmpty()) {
            addBubble(greeting(), false)
            addBubble(
                "Спроси что угодно — или включи капсулу, и я буду поверх всех приложений.",
                false
            )
        } else {
            for (m in messages) addBubble(m.content, m.role == "user")
        }
        scrollDown()
    }

    private fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Доброе утро. Я Никси — спрашивай что угодно."
            in 12..17 -> "Добрый день. Я на связи, говори."
            in 18..22 -> "Добрый вечер. Чем помочь?"
            else -> "Не спится? Я тут."
        }
    }

    private fun bubbleView(text: String, mine: Boolean): View {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(if (mine) 0xFF0B0B0F.toInt() else 0xFFEDEDF3.toInt())
            textSize = 15f
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            background = round(
                if (mine) 0xFFFF7A18.toInt() else 0xFF1A1A22.toInt(),
                dp(18).toFloat(),
                if (mine) 0 else 1,
                if (mine) 0 else 0x14FFFFFF
            )
            maxWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt()
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(0, dp(4), 0, dp(4))
            addView(
                tv,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return row
    }

    private fun addBubble(text: String, mine: Boolean): View {
        val view = bubbleView(text, mine)
        chatList.addView(view)
        scrollDown()
        return view
    }

    private fun scrollDown() {
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun quick(text: String) {
        etInput.setText(text)
        send()
    }

    private fun send() {
        if (busy) {
            Toast.makeText(this, "Подожди, я отвечаю…", Toast.LENGTH_SHORT).show()
            return
        }
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return

        etInput.setText("")
        addBubble(text, true)
        Prefs.appendMessage(this, chatId, "user", text)

        val key = Prefs.apiKey(this)
        if (key.isBlank()) {
            ui.postDelayed({
                addBubble("Сначала впиши ключ OpenRouter в настройках — без него я не смогу ответить.", false)
                refreshDrawer()
            }, 350)
            return
        }

        busy = true
        val thinking = addBubble("печатает…", false)
        val messages = buildMessages()
        val model = Prefs.textModel(this)

        Thread {
            val answer = try {
                AiClient.chat(key, model, messages)
            } catch (t: Throwable) {
                "Ошибка: ${t.message ?: "нет сети"}"
            }
            ui.post {
                busy = false
                chatList.removeView(thinking)
                addBubble(answer, false)
                Prefs.appendMessage(this, chatId, "assistant", answer)
                refreshDrawer()
            }
        }.start()
    }

    private fun buildMessages(): JSONArray {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", Prefs.persona(this)))
        val chat = Prefs.chatById(this, chatId)
        chat?.messages?.takeLast(12)?.forEach {
            arr.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        return arr
    }

    private fun startNewChat() {
        chatId = Prefs.newChatId()
        Prefs.setCurrentChat(this, chatId)
        renderCurrentChat()
        refreshDrawer()
    }

    private fun openChat(id: String) {
        chatId = id
        Prefs.setCurrentChat(this, id)
        renderCurrentChat()
        closeDrawer()
    }

    // ---------------- панель слева ----------------

    private fun refreshDrawer() {
        drawerHistory.removeAllViews()
        val chats = Prefs.chats(this).filter { it.messages.isNotEmpty() }

        if (chats.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Пока пусто"
                setTextColor(0xFF5E5E6B.toInt())
                textSize = 13f
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            drawerHistory.addView(empty)
            return
        }

        val fmt = SimpleDateFormat("d MMM, HH:mm", ru)
        for (chat in chats) {
            val row = TextView(this).apply {
                text = chat.title
                setTextColor(if (chat.id == chatId) 0xFFFF7A18.toInt() else 0xFFD2D2DC.toInt())
                textSize = 14f
                maxLines = 1
                setPadding(dp(10), dp(11), dp(10), dp(11))
                background = round(
                    if (chat.id == chatId) 0x14FF7A18 else 0x00000000,
                    dp(12).toFloat()
                )
            }
            val sub = TextView(this).apply {
                text = "${chat.messages.size} сообщ. · ${fmt.format(Date(chat.updated))}"
                setTextColor(0xFF5E5E6B.toInt())
                textSize = 10f
                setPadding(dp(10), 0, dp(10), dp(8))
            }
            row.setOnClickListener { openChat(chat.id) }
            row.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Удалить чат?")
                    .setMessage(chat.title)
                    .setPositiveButton("Удалить") { _, _ ->
                        Prefs.deleteChat(this, chat.id)
                        if (chat.id == chatId) startNewChat()
                        refreshDrawer()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
                true
            }
            drawerHistory.addView(row)
            drawerHistory.addView(sub)
        }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        refreshDrawer()
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(200).start()
        drawer.animate().translationX(0f).setDuration(220).start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        drawer.animate().translationX(-dp(304).toFloat()).setDuration(200).start()
        scrim.animate().alpha(0f).setDuration(180).withEndAction {
            scrim.visibility = View.GONE
        }.start()
    }

    @Deprecated("Для старых версий Android")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer() else super.onBackPressed()
    }

    // ---------------- капсула и голос ----------------

    private fun toggleCapsule() {
        if (CapsuleService.running) {
            stopService(Intent(this, CapsuleService::class.java))
            Toast.makeText(this, "Капсула остановлена", Toast.LENGTH_SHORT).show()
            ui.postDelayed({ updateStatus() }, 400)
            closeDrawer()
            return
        }
        if (Prefs.apiKey(this).isBlank()) {
            Toast.makeText(this, "Сначала впиши ключ в настройках", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startForegroundService(Intent(this, CapsuleService::class.java))
            Toast.makeText(this, "Капсула сверху по центру", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Не удалось запустить: ${t.message}", Toast.LENGTH_LONG).show()
        }
        ui.postDelayed({ updateStatus() }, 600)
        closeDrawer()
    }

    private fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори…")
        }
        try {
            startActivityForResult(intent, 11)
        } catch (t: Throwable) {
            Toast.makeText(this, "Распознавание речи недоступно", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Для старых версий Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11 && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) quick(text)
        }
    }
}
