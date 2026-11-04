package com.nagibatorz.nixi

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
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
        private val WAKE_WORDS = listOf("никси", "никс и", "ники", "никс")
    }

    private enum class Mode { WAKE, COMMAND }

    private lateinit var windowManager: WindowManager
    private lateinit var panel: View
    private lateinit var tvState: TextView
    private lateinit var tvAnswer: TextView
    private lateinit var actions: View
    private lateinit var dot: View
    private lateinit var params: WindowManager.LayoutParams

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var mode = Mode.COMMAND
    private var listening = false
    private var busy = false
    private var dotAnim: ObjectAnimator? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    private val ru: Locale = Locale.forLanguageTag("ru")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildOverlay()
        tts = TextToSpeech(this, this)
        Bus.listener = { text -> respond(text) }
        if (Prefs.wakeWord(this)) {
            handler.postDelayed({ listen(Mode.WAKE) }, 900)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts?.language = Locale.forLanguageTag("ru-RU")
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                handler.post { scheduleWake(1200) }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                handler.post { scheduleWake(1200) }
            }
        })
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
            .setContentText("Скажи «Никси» или нажми капсулу")
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
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            val micGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (micGranted) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun rounded(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusPx
        }

    private fun buildOverlay() {
        panel = LayoutInflater.from(this).inflate(R.layout.capsule, null)
        tvState = panel.findViewById(R.id.tvState)
        tvAnswer = panel.findViewById(R.id.tvAnswer)
        actions = panel.findViewById(R.id.actions)
        dot = panel.findViewById(R.id.viewDot)

        val capsuleRow = panel.findViewById<View>(R.id.capsuleRow)
        capsuleRow.background = rounded(0xFF121212.toInt(), 999f)
        actions.background = rounded(0xFF1A1A1A.toInt(), 48f)
        dot.background = rounded(0xFFFF7A18.toInt(), 999f)

        panel.findViewById<View>(R.id.btnMic).setOnClickListener {
            if (busy) {
                setState("подожди, отвечаю")
            } else {
                stopListen()
                listen(Mode.COMMAND)
            }
        }
        panel.findViewById<View>(R.id.btnScreen).setOnClickListener { openScreen() }
        panel.findViewById<View>(R.id.btnClose).setOnClickListener {
            hideAnswerPanel()
            setState("Никси")
            scheduleWake(400)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 120
        }

        capsuleRow.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (Math.abs(dx) > 16 || Math.abs(dy) > 16) dragging = true
                    if (dragging) {
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            windowManager.updateViewLayout(panel, params)
                        } catch (t: Throwable) {
                            // окно уже убрали
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) onCapsuleTap()
                    dragging = false
                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(panel, params)
            panel.post {
                val screenWidth = resources.displayMetrics.widthPixels
                if (panel.width > 0 && panel.width < screenWidth) {
                    params.x = (screenWidth - panel.width) / 2
                    try {
                        windowManager.updateViewLayout(panel, params)
                    } catch (t: Throwable) {
                        // окно уже убрали
                    }
                }
            }
        } catch (t: Throwable) {
            Toast.makeText(this, "Нет разрешения «поверх других приложений»", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun onCapsuleTap() {
        if (busy) {
            setState("подожди, отвечаю")
            return
        }
        if (listening && mode == Mode.COMMAND) {
            stopListen()
            setState("Никси")
            scheduleWake(300)
            return
        }
        stopListen()
        listen(Mode.COMMAND)
    }

    private fun startDotPulse() {
        if (dotAnim == null) {
            dotAnim = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.25f).apply {
                duration = 650
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
            }
        }
        dotAnim?.start()
    }

    private fun stopDotPulse() {
        try {
            dotAnim?.cancel()
        } catch (t: Throwable) {
            // ничего
        }
        dotAnim = null
        dot.alpha = 1f
    }

    private fun listen(m: Mode) {
        if (busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (m == Mode.COMMAND) {
                respond("На телефоне не включено распознавание речи. Проверь Google и русский язык в настройках.")
            }
            return
        }

        stopListen()
        mode = m
        listening = true

        if (m == Mode.COMMAND) {
            hideAnswerPanel()
            setState("слушаю…")
            startDotPulse()
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(recognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, m == Mode.COMMAND)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (m == Mode.WAKE) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L
                )
            }
        }

        try {
            recognizer?.startListening(intent)
        } catch (t: Throwable) {
            listening = false
            stopDotPulse()
            if (m == Mode.COMMAND) setState("микрофон не ответил")
        }
    }

    private fun stopListen() {
        listening = false
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (t: Throwable) {
            // ничего
        }
        recognizer = null
        stopDotPulse()
    }

    private fun scheduleWake(delay: Long = 500) {
        if (!Prefs.wakeWord(this) || busy) return
        handler.postDelayed({
            if (!listening && !busy && Prefs.wakeWord(this)) {
                listen(Mode.WAKE)
            }
        }, delay)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (mode == Mode.COMMAND) setState("говори…")
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (mode == Mode.COMMAND) setState("думаю…")
        }

        override fun onError(error: Int) {
            listening = false
            stopDotPulse()

            if (mode == Mode.WAKE) {
                handler.postDelayed({
                    if (!busy && Prefs.wakeWord(this@CapsuleService)) listen(Mode.WAKE)
                }, 400)
                return
            }

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> setState("не расслышал")

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    setState("нет доступа к микрофону")
                    respond("Дай доступ к микрофону в настройках Nixi")
                }

                else -> setState("ошибка распознавания")
            }
            scheduleWake(1500)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            stopDotPulse()
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()

            if (mode == Mode.WAKE) {
                if (isWake(text)) {
                    setState("слушаю…")
                    speakOut("Да?")
                    handler.postDelayed({ listen(Mode.COMMAND) }, 700)
                } else {
                    scheduleWake(300)
                }
                return
            }

            if (text.isBlank()) {
                setState("пусто, попробуй снова")
                scheduleWake(1200)
                return
            }
            handleCommand(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (mode != Mode.COMMAND) return
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) setState(partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun isWake(text: String): Boolean {
        val t = text.lowercase(ru).replace('ё', 'е')
        return WAKE_WORDS.any { t.contains(it) }
    }

    private fun handleCommand(raw: String) {
        val text = raw.lowercase(ru).replace('ё', 'е')

        when {
            text.contains("очисти историю") || text.contains("забудь всё") -> {
                Prefs.clearHistory(this)
                respond("Историю очистил")
                return
            }

            text.contains("что на экране") ||
                text.contains("проанализируй") ||
                text.contains("посмотри экран") -> {
                openScreen()
                return
            }

            text.contains("который час") || text.contains("сколько времени") -> {
                val time = SimpleDateFormat("HH:mm", ru).format(Date())
                respond("Сейчас $time")
                return
            }

            text.contains("привет") && text.length < 25 -> {
                respond("Привет! Я тут. Скажи «Никси» или просто спроси что угодно.")
                return
            }

            text.contains("закройся") || text.contains("спрячься") -> {
                hideAnswerPanel()
                setState("Никси")
                scheduleWake(300)
                return
            }
        }

        val action = Actions.tryHandle(this, raw)
        if (action != null) {
            respond(action)
            return
        }

        askAi(raw)
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
            put(JSONObject().put("role", "system").put("content", Prefs.persona(this@CapsuleService)))
            for (turn in Prefs.history(this@CapsuleService)) {
                put(JSONObject().put("role", turn.role).put("content", turn.content))
            }
            put(JSONObject().put("role", "user").put("content", prompt))
        }

        Prefs.addTurn(this, "user", prompt)

        val model = Prefs.textModel(this)
        Thread {
            val answer = try {
                AiClient.chat(key, model, messages)
            } catch (t: Throwable) {
                "Ошибка сети: ${t.message ?: "неизвестно"}"
            }
            handler.post {
                busy = false
                if (answer.isNotBlank()) Prefs.addTurn(this, "assistant", answer)
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
        handler.removeCallbacksAndMessages(null)
        stopListen()
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
