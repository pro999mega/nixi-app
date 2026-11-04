package com.nagibatorz.nixi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Канал связи между экраном снимка и капсулой. */
object Bus {
    var listener: ((String) -> Unit)? = null
    fun emit(text: String) {
        listener?.invoke(text)
    }
}

class CapsuleService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val CHANNEL_ID = "nixi"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.nagibatorz.nixi.STOP"
        const val SYSTEM_PROMPT =
            "Ты Никси — голосовой ассистент внутри телефона. Отвечай по-русски, дружелюбно и очень коротко: " +
                "одно-три предложения, без списков, без markdown и без эмодзи, потому что твой ответ читается вслух."
    }

    private lateinit var windowManager: WindowManager
    private lateinit var panel: View
    private lateinit var tvState: TextView
    private lateinit var tvAnswer: TextView
    private lateinit var actions: View
    private lateinit var params: WindowManager.LayoutParams

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var listening = false
    private var busy = false
    private var downRawY = 0f
    private var startY = 0
    private var dragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildOverlay()
        tts = TextToSpeech(this, this)
        Bus.listener = { text -> respond(text) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("ru", "RU")
        }
    }

    private fun startAsForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Nixi", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Капсула ассистента поверх экрана"
            manager.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopCapsule = PendingIntent.getService(
            this, 2, Intent(this, CapsuleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Nixi на связи")
            .setContentText("Нажми капсулу сверху и говори")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openApp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopCapsule
                ).build()
            )
        }

        val notification = builder.build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildOverlay() {
        panel = LayoutInflater.from(this).inflate(R.layout.capsule, null)
        tvState = panel.findViewById(R.id.tvState)
        tvAnswer = panel.findViewById(R.id.tvAnswer)
        actions = panel.findViewById(R.id.actions)

        panel.findViewById<View>(R.id.btnMic).setOnClickListener { startListening() }
        panel.findViewById<View>(R.id.btnScreen).setOnClickListener { openScreen() }
        panel.findViewById<View>(R.id.btnClose).setOnClickListener {
            hideAnswerPanel()
            setState("здесь")
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
        }

        panel.findViewById<View>(R.id.capsuleRow).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = event.rawY
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downRawY
                    if (Math.abs(dy) > 18f) {
                        dragging = true
                        params.y = (startY + dy).toInt()
                        windowManager.updateViewLayout(panel, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) startListening()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(panel, params)
        } catch (t: Throwable) {
            Toast.makeText(this, "Нет разрешения «поверх других приложений»", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun startListening() {
        if (listening) return
        if (busy) {
            setState("подожди, отвечаю")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            respond("На телефоне не включено распознавание речи. Проверь «Google» и русский язык в настройках.")
            return
        }
        listening = true
        hideAnswerPanel()
        setState("слушаю…")

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(recognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            listening = false
            setState("микрофон не ответил")
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            setState("говори…")
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            setState("думаю…")
        }

        override fun onError(error: Int) {
            listening = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> setState("не расслышал")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    setState("нет доступа к микрофону")
                    respond("Дай доступ к микрофону в настройках Nixi")
                }
                else -> setState("ошибка распознавания")
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isBlank()) {
                setState("пусто, попробуй снова")
                return
            }
            handleCommand(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) setState(partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun handleCommand(raw: String) {
        val text = raw.lowercase(Locale("ru"))
        when {
            text.contains("что на экране") ||
                text.contains("проанализируй") ||
                text.contains("посмотри экран") -> openScreen()

            text.contains("который час") || text.contains("сколько времени") -> {
                val time = SimpleDateFormat("HH:mm", Locale("ru")).format(Date())
                respond("Сейчас $time")
            }

            text.contains("привет") || text.contains("здравствуй") ->
                respond("Привет! Я тут. Скажи «что на экране» или просто спроси что угодно.")

            text.contains("закройся") || text.contains("спрячься") -> {
                hideAnswerPanel()
                setState("здесь")
            }

            else -> askAi(raw)
        }
    }

    private fun askAi(prompt: String) {
        val key = Prefs.apiKey(this)
        if (key.isBlank()) {
            respond("Впиши ключ OpenRouter в приложении Nixi и нажми «Сохранить».")
            return
        }
        busy = true
        setState("думаю…")

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            put(JSONObject().put("role", "user").put("content", prompt))
        }

        val model = Prefs.textModel(this)
        Thread {
            val answer = AiClient.chat(key, model, messages)
            handler.post {
                busy = false
                respond(answer)
            }
        }.start()
    }

    private fun openScreen() {
        setState("смотрю экран…")
        val intent = Intent(this, ScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            respond("Не получилось открыть захват экрана: ${t.message ?: "неизвестно"}")
        }
    }

    private fun respond(text: String) {
        tvAnswer.text = text
        tvAnswer.visibility = View.VISIBLE
        actions.visibility = View.VISIBLE
        setState("готово")
        speakOut(text)
    }

    private fun speakOut(text: String) {
        val clean = text.replace(Regex("[*_#`>]"), "").replace("\n", " ").take(600)
        if (clean.isBlank()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "nixi")
    }

    private fun setState(text: String) {
        tvState.text = text
    }

    private fun hideAnswerPanel() {
        tvAnswer.visibility = View.GONE
        actions.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        Bus.listener = null
        try {
            recognizer?.destroy()
        } catch (t: Throwable) {
            // ничего
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        try {
            windowManager.removeView(panel)
        } catch (t: Throwable) {
            // ничего
        }
    }
}
