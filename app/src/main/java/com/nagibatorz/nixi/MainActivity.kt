package com.nagibatorz.nixi

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Random

class MainActivity : AppCompatActivity() {

    private data class Message(val role: String, val text: String)

    private class SakuraBackgroundView(context: Context) : View(context) {
        private class Petal(
            var x: Float,
            var y: Float,
            var size: Float,
            var speedY: Float,
            var speedX: Float,
            var angle: Float,
            var spinSpeed: Float,
            var alpha: Int,
            var color: Int
        )

        private val petals = mutableListOf<Petal>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val random = Random()
        private val handler = Handler(Looper.getMainLooper())
        private val animator = object : Runnable {
            override fun run() {
                updatePetals()
                invalidate()
                handler.postDelayed(this, 35)
            }
        }

        init {
            val colors = intArrayOf(
                Color.parseColor("#FFD6E7"),
                Color.parseColor("#FFB3C9"),
                Color.parseColor("#F7A8C4")
            )
            for (i in 0 until 22) {
                petals.add(
                    Petal(
                        x = random.nextFloat() * 1080f,
                        y = random.nextFloat() * 1920f,
                        size = 6f + random.nextFloat() * 8f,
                        speedY = 1.2f + random.nextFloat() * 1.8f,
                        speedX = -0.5f + random.nextFloat() * 1.0f,
                        angle = random.nextFloat() * 360f,
                        spinSpeed = -2f + random.nextFloat() * 4f,
                        alpha = 50 + random.nextInt(81),
                        color = colors[random.nextInt(colors.size)]
                    )
                )
            }
            handler.post(animator)
        }

        private fun updatePetals() {
            val w = if (width > 0) width.toFloat() else 1080f
            val h = if (height > 0) height.toFloat() else 1920f
            for (p in petals) {
                p.y += p.speedY
                p.x += p.speedX + Math.sin((p.y * 0.01).toDouble()).toFloat() * 0.5f
                p.angle += p.spinSpeed
                if (p.y > h + 30f) {
                    p.y = -30f
                    p.x = random.nextFloat() * w
                }
                if (p.x < -30f) p.x = w + 30f
                if (p.x > w + 30f) p.x = -30f
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (p in petals) {
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.angle)
                paint.color = p.color
                paint.alpha = p.alpha
                path.reset()
                val s = p.size
                path.moveTo(0f, -s)
                path.quadTo(s * 0.8f, -s * 0.3f, 0f, s)
                path.quadTo(-s * 0.8f, -s * 0.3f, 0f, -s)
                canvas.drawPath(path, paint)
                canvas.restore()
            }
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            handler.removeCallbacks(animator)
        }
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var inputField: EditText
    private lateinit var chatsListContainer: LinearLayout

    private val messages = mutableListOf<Message>()
    private var typingIndicatorView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = createRootLayout()
        setContentView(root)

        loadHistory()
        renderChatMessages()
        renderDrawerChats()
        checkApiKeyAndPrompt()
    }

    private fun checkApiKeyAndPrompt() {
        val sp = getSharedPreferences("nixi", Context.MODE_PRIVATE)
        val apiKey = sp.getString("apiKey", "").orEmpty().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Добавь ключ OpenRouter в настройках", Toast.LENGTH_LONG).show()
            openSettings()
        }
    }

    private fun openSettings() {
        runCatching {
            startActivity(Intent(this, Class.forName("com.nagibatorz.nixi.SettingsActivity")))
        }.onFailure {
            Toast.makeText(this, "Экран настроек пока недоступен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCapsule() {
        runCatching {
            startService(Intent().setClassName(packageName, "com.nagibatorz.nixi.CapsuleService"))
        }.onFailure {
            Toast.makeText(this, "Сервис капсулы пока недоступен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun round(
        color: Int,
        radiusDp: Float,
        strokeWidthDp: Int,
        strokeColor: Int
    ): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        if (radiusDp > 0f) {
            d.cornerRadius = radiusDp * resources.displayMetrics.density
        }
        d.setColor(color)
        if (strokeWidthDp > 0) {
            d.setStroke((strokeWidthDp * resources.displayMetrics.density).toInt(), strokeColor)
        }
        return d
    }

    private fun roundGradient(
        colors: IntArray,
        orientation: GradientDrawable.Orientation,
        radiusDp: Float,
        strokeWidthDp: Int,
        strokeColor: Int
    ): GradientDrawable {
        val d = GradientDrawable(orientation, colors)
        d.shape = GradientDrawable.RECTANGLE
        if (radiusDp > 0f) {
            d.cornerRadius = radiusDp * resources.displayMetrics.density
        }
        if (strokeWidthDp > 0) {
            d.setStroke((strokeWidthDp * resources.displayMetrics.density).toInt(), strokeColor)
        }
        return d
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun createRootLayout(): View {
        drawerLayout = DrawerLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#120A10"))
        }

        val frameRoot = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val bgView = SakuraBackgroundView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        frameRoot.addView(bgView)

        val mainContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = round(Color.parseColor("#221420"), 0f, 1, Color.parseColor("#3A2130"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val menuBtn = TextView(this).apply {
            text = "☰"
            textSize = 24f
            setTextColor(Color.parseColor("#F7EEF3"))
            setPadding(dp(4), dp(4), dp(12), dp(4))
            setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        val titleView = TextView(this).apply {
            text = "Никси"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            post {
                val shader = LinearGradient(
                    0f, 0f, measuredWidth.toFloat(), 0f,
                    Color.parseColor("#FF8FB1"),
                    Color.parseColor("#FFB37A"),
                    Shader.TileMode.CLAMP
                )
                paint.shader = shader
                invalidate()
            }
        }

        val addChatCircle = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = round(Color.parseColor("#221420"), 20f, 1, Color.parseColor("#3A2130"))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener {
                clearCurrentChat()
            }
        }
        val plusText = TextView(this).apply {
            text = "＋"
            textSize = 20f
            setTextColor(Color.parseColor("#FF8FB1"))
            gravity = Gravity.CENTER
        }
        addChatCircle.addView(plusText)

        header.addView(menuBtn)
        header.addView(titleView)
        header.addView(addChatCircle)
        mainContent.addView(header)

        // Chips
        val chipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val chipsFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val chipsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        chipsContainer.addView(createChip("Капсула") {
            startCapsule()
        })
        chipsContainer.addView(createChip("Новый чат") {
            clearCurrentChat()
        })
        chipsContainer.addView(createChip("Настройки") {
            openSettings()
        })

        chipsFrame.addView(chipsContainer)
        chipsScroll.addView(chipsFrame)
        mainContent.addView(chipsScroll)

        // Chat list
        chatScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val chatScrollFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        chatScrollFrame.addView(chatContainer)
        chatScrollView.addView(chatScrollFrame)
        mainContent.addView(chatScrollView)

        // Bottom input
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = round(Color.parseColor("#221420"), 0f, 1, Color.parseColor("#3A2130"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputField = EditText(this).apply {
            hint = "Сообщение…"
            setHintTextColor(Color.parseColor("#A98B9C"))
            setTextColor(Color.parseColor("#F7EEF3"))
            textSize = 15f
            background = round(Color.parseColor("#1A0E15"), 24f, 1, Color.parseColor("#3A2130"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(10)
            }
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    handleSend()
                    true
                } else {
                    false
                }
            }
        }

        val sendBtn = ImageButton(this).apply {
            setImageDrawable(androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_send))
            background = roundGradient(
                intArrayOf(Color.parseColor("#E05C8A"), Color.parseColor("#FF8FB1")),
                GradientDrawable.Orientation.TL_BR,
                25f, 0, 0
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50))
            setOnClickListener {
                handleSend()
            }
        }

        bottomBar.addView(inputField)
        bottomBar.addView(sendBtn)
        mainContent.addView(bottomBar)

        frameRoot.addView(mainContent)
        drawerLayout.addView(frameRoot)

        val drawerView = createDrawerView()
        val drawerParams = DrawerLayout.LayoutParams(
            dp(300),
            DrawerLayout.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = GravityCompat.START
        }
        drawerView.layoutParams = drawerParams
        drawerLayout.addView(drawerView)

        return drawerLayout
    }

    private fun createChip(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#F7EEF3"))
            background = round(Color.parseColor("#221420"), 18f, 1, Color.parseColor("#3A2130"))
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                rightMargin = dp(8)
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    }
                }
                false
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createDrawerView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#170D13"))
            setPadding(dp(16), dp(36), dp(16), dp(16))
            isClickable = true
            isFocusable = true
        }

        val title = TextView(this).apply {
            text = "Чаты"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F7EEF3"))
            setPadding(0, 0, 0, dp(16))
        }
        layout.addView(title)

        val newChatBtn = TextView(this).apply {
            text = "＋ Новый чат"
            textSize = 15f
            setTextColor(Color.parseColor("#FF8FB1"))
            background = round(Color.parseColor("#221420"), 16f, 1, Color.parseColor("#3A2130"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                clearCurrentChat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }
        layout.addView(newChatBtn)

        val chatsScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val chatsFrame = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        chatsListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        chatsFrame.addView(chatsListContainer)
        chatsScroll.addView(chatsFrame)
        layout.addView(chatsScroll)

        val settingsBtn = TextView(this).apply {
            text = "⚙ Настройки"
            textSize = 15f
            setTextColor(Color.parseColor("#A98B9C"))
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                openSettings()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }
        layout.addView(settingsBtn)

        return layout
    }

    private fun renderDrawerChats() {
        chatsListContainer.removeAllViews()
        val item = TextView(this).apply {
            text = if (messages.isNotEmpty()) messages[0].text.take(24) + "..." else "Текущий диалог"
            textSize = 14f
            setTextColor(Color.parseColor("#F7EEF3"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = round(Color.parseColor("#221420"), 12f, 1, Color.parseColor("#3A2130"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }
        chatsListContainer.addView(item)
    }

    private fun loadHistory() {
        messages.clear()
        val sp = getSharedPreferences("nixi_chats", Context.MODE_PRIVATE)
        val jsonStr = sp.getString("messages", null) ?: return
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                messages.add(Message(obj.getString("role"), obj.getString("text")))
            }
        } catch (e: Exception) {
        }
    }

    private fun saveHistory() {
        val sp = getSharedPreferences("nixi_chats", Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (m in messages) {
            val obj = JSONObject()
            obj.put("role", m.role)
            obj.put("text", m.text)
            arr.put(obj)
        }
        sp.edit().putString("messages", arr.toString()).apply()
    }

    private fun clearCurrentChat() {
        messages.clear()
        saveHistory()
        renderChatMessages()
        renderDrawerChats()
    }

    private fun renderChatMessages() {
        chatContainer.removeAllViews()
        for (m in messages) {
            addBubble(m.role, m.text, false)
        }
        if (typingIndicatorView != null) {
            chatContainer.addView(typingIndicatorView)
        }
        scrollToBottom()
    }

    private fun addBubble(role: String, text: String, animate: Boolean) {
        val isUser = role == "user"
        val bubbleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = if (isUser) {
                roundGradient(
                    intArrayOf(Color.parseColor("#E05C8A"), Color.parseColor("#FF8FB1")),
                    GradientDrawable.Orientation.TL_BR,
                    22f, 0, 0
                )
            } else {
                round(Color.parseColor("#221420"), 22f, 1, Color.parseColor("#3A2130"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
                bottomMargin = dp(10)
            }
        }

        val tv = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(if (isUser) Color.parseColor("#1A0E15") else Color.parseColor("#F7EEF3"))
            maxWidth = (resources.displayMetrics.widthPixels * 0.84).toInt()
        }
        bubbleLayout.addView(tv)
        chatContainer.addView(bubbleLayout)

        if (animate) {
            bubbleLayout.alpha = 0f
            bubbleLayout.translationY = dp(14).toFloat()
            bubbleLayout.animate().alpha(1f).translationY(0f).setDuration(260).start()
        }
    }

    private fun showTypingIndicator() {
        if (typingIndicatorView != null) return
        val bubbleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = round(Color.parseColor("#221420"), 22f, 1, Color.parseColor("#3A2130"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                bottomMargin = dp(10)
            }
        }

        val tv = TextView(this).apply {
            text = "печатает…"
            textSize = 14f
            setTextColor(Color.parseColor("#A98B9C"))
        }
        bubbleLayout.addView(tv)
        typingIndicatorView = bubbleLayout
        chatContainer.addView(bubbleLayout)
        scrollToBottom()
    }

    private fun removeTypingIndicator() {
        val v = typingIndicatorView
        if (v != null) {
            chatContainer.removeView(v)
            typingIndicatorView = null
        }
    }

    private fun scrollToBottom() {
        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun handleSend() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return
        inputField.setText("")

        messages.add(Message("user", text))
        saveHistory()
        addBubble("user", text, true)
        scrollToBottom()
        renderDrawerChats()

        showTypingIndicator()

        Thread {
            callApi(text)
        }.start()
    }

    private fun callApi(userText: String) {
        try {
            val sp = getSharedPreferences("nixi", Context.MODE_PRIVATE)
            val apiKey = sp.getString("apiKey", "").orEmpty().trim()
            if (apiKey.isEmpty()) {
                runOnUiThread {
                    removeTypingIndicator()
                    Toast.makeText(this, "Добавь ключ OpenRouter в настройках", Toast.LENGTH_LONG).show()
                    openSettings()
                }
                return
            }

            val textModel = sp.getString("textModel", "google/gemma-4-31b-it:free").orEmpty().ifBlank { "google/gemma-4-31b-it:free" }
            val persona = sp.getString("persona", "Ты Никси — голосовой ассистент внутри телефона. Отвечай по-русски, дружелюбно и очень коротко: одно-три предложения, без списков, без markdown и без эмодзи.").orEmpty()

            val url = URL("https://openrouter.ai/api/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 20000

            val messagesArray = JSONArray()
            if (persona.isNotEmpty()) {
                messagesArray.put(JSONObject().put("role", "system").put("content", persona))
            }

            val slice = messages.takeLast(12)
            for (m in slice) {
                messagesArray.put(JSONObject().put("role", m.role).put("content", m.text))
            }

            val body = JSONObject()
            body.put("model", textModel)
            body.put("messages", messagesArray)

            conn.outputStream.use { os ->
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                os.write(bytes, 0, bytes.size)
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseStr = InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }

            if (code in 200..299) {
                val json = JSONObject(responseStr)
                val choices = json.getJSONArray("choices")
                if (choices.length() > 0) {
                    val content = choices.getJSONObject(0).getJSONObject("message").getString("content").trim()
                    runOnUiThread {
                        removeTypingIndicator()
                        messages.add(Message("assistant", content))
                        saveHistory()
                        addBubble("assistant", content, true)
                        scrollToBottom()
                        renderDrawerChats()
                    }
                } else {
                    handleError()
                }
            } else {
                handleError()
            }
        } catch (e: Exception) {
            handleError()
        }
    }

    private fun handleError() {
        runOnUiThread {
            removeTypingIndicator()
            val err = "Не удалось получить ответ"
            messages.add(Message("assistant", err))
            saveHistory()

            val bubbleLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = round(Color.parseColor("#221420"), 22f, 1, Color.parseColor("#3A2130"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.START
                    bottomMargin = dp(10)
                }
            }
            val tv = TextView(this).apply {
                text = err
                textSize = 15f
                setTextColor(Color.parseColor("#A98B9C"))
            }
            bubbleLayout.addView(tv)
            chatContainer.addView(bubbleLayout)
            scrollToBottom()
        }
    }
}
