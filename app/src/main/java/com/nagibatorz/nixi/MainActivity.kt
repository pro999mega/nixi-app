package com.nagibatorz.nixi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etKey: EditText
    private lateinit var etTextModel: EditText
    private lateinit var etVisionModel: EditText
    private lateinit var etPersona: EditText
    private lateinit var swWake: SwitchCompat
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etKey = findViewById(R.id.etKey)
        etTextModel = findViewById(R.id.etTextModel)
        etVisionModel = findViewById(R.id.etVisionModel)
        etPersona = findViewById(R.id.etPersona)
        swWake = findViewById(R.id.swWake)
        tvStatus = findViewById(R.id.tvStatus)

        etKey.setText(Prefs.apiKey(this))
        etTextModel.setText(Prefs.textModel(this))
        etVisionModel.setText(Prefs.visionModel(this))
        etPersona.setText(Prefs.persona(this))
        swWake.isChecked = Prefs.wakeWord(this)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.setApiKey(this, etKey.text.toString())
            Prefs.setModels(this, etTextModel.text.toString(), etVisionModel.text.toString())
            Prefs.setPersona(this, etPersona.text.toString())
            Prefs.setWakeWord(this, swWake.isChecked)
            refreshStatus()
            toast("Сохранено")
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        findViewById<Button>(R.id.btnMic).setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 7)
        }

        findViewById<Button>(R.id.btnNotifications).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8
                )
            } else {
                toast("На этой версии Android уведомления не спрашиваются")
            }
        }

        findViewById<Button>(R.id.btnClearHistory).setOnClickListener {
            Prefs.clearHistory(this)
            toast("История диалога очищена")
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener { startCapsule() }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, CapsuleService::class.java))
            toast("Капсула остановлена")
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 8
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        val mic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val key = Prefs.apiKey(this).isNotBlank()
        val wake = Prefs.wakeWord(this)

        tvStatus.text = buildString {
            append(if (overlay) "✅ Поверх приложений разрешено" else "❌ Поверх приложений — нажми кнопку 1")
            append("\n")
            append(if (mic) "✅ Микрофон разрешён" else "❌ Микрофон — нажми кнопку 2")
            append("\n")
            append(if (key) "✅ Ключ OpenRouter есть" else "❌ Ключ OpenRouter не вписан")
            append("\n")
            append(if (wake) "✅ Кодовое слово «Никси» включено" else "⭕ Кодовое слово выключено")
            val turns = Prefs.history(this).size
            append("\n")
            append("💬 В истории диалога реплик: $turns")
        }
    }

    private fun startCapsule() {
        if (!Settings.canDrawOverlays(this)) {
            toast("Сначала разреши «Поверх других приложений»")
            return
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            toast("Сначала разреши микрофон")
            return
        }
        if (Prefs.apiKey(this).isBlank()) {
            toast("Впиши ключ OpenRouter и нажми «Сохранить»")
            return
        }
        Prefs.setWakeWord(this, swWake.isChecked)
        ContextCompat.startForegroundService(this, Intent(this, CapsuleService::class.java))
        toast(
            if (swWake.isChecked) "Капсула сверху. Скажи «Никси» или нажми на неё"
            else "Капсула сверху — нажми на неё и говори"
        )
        moveTaskToBack(true)
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
