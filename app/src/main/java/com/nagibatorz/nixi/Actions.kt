package com.nagibatorz.nixi

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import java.util.Locale

/**
 * Разбирает голосовую команду и, если это действие, выполняет его.
 * Возвращает короткий ответ для озвучки или null, если это не действие —
 * тогда фраза уходит в модель.
 */
object Actions {

    fun tryHandle(c: Context, raw: String): String? {
        val t = raw.lowercase(Locale("ru")).replace('ё', 'е').trim()
        if (t.isBlank()) return null

        if (t.contains("фонарик") || t.contains("фонарь") || t.contains("подсвети")) {
            return torch(c, t)
        }
        if (t.contains("будильник")) return alarm(c, t)
        if (t.contains("таймер")) return timer(c, t)
        if (t.startsWith("позвони") || t.startsWith("набери") || t.contains("позвонить")) {
            return call(c, t)
        }
        if (t.startsWith("открой") || t.startsWith("запусти") || t.startsWith("включи приложение")) {
            return openApp(c, t)
        }
        if (t == "громче" || t.contains("сделай громче") || t.contains("прибавь звук")) {
            return volume(c, up = true)
        }
        if (t == "тише" || t.contains("сделай тише") || t.contains("убавь звук")) {
            return volume(c, up = false)
        }
        return null
    }

    private fun torch(c: Context, t: String): String {
        val manager = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = try {
            manager.cameraIdList.firstOrNull { camId ->
                manager.getCameraCharacteristics(camId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        } ?: return "На этом телефоне не нашёл фонарик"

        val turnOff = t.contains("выключ") || t.contains("погаси") || t.contains("отключ")
        return try {
            manager.setTorchMode(id, !turnOff)
            if (turnOff) "Фонарик выключил" else "Фонарик включил"
        } catch (e: Exception) {
            "Не получилось переключить фонарик"
        }
    }

    private fun alarm(c: Context, t: String): String? {
        val nums = numbers(t)
        if (nums.isEmpty()) return null
        var hour = nums[0]
        var minute = if (nums.size > 1) nums[1] else 0
        if (t.contains("вечера") && hour in 1..11) hour += 12
        if (hour > 23) hour = hour % 24
        if (minute > 59) minute = 0

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            c.startActivity(intent)
            String.format(Locale("ru"), "Будильник на %02d:%02d", hour, minute)
        } catch (e: Exception) {
            "Не получилось поставить будильник"
        }
    }

    private fun timer(c: Context, t: String): String? {
        val n = numbers(t).firstOrNull() ?: return null
        val seconds = when {
            t.contains("секунд") -> n
            t.contains("час") -> n * 3600
            else -> n * 60
        }
        if (seconds <= 0) return null

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            c.startActivity(intent)
            val m = seconds / 60
            val s = seconds % 60
            when {
                m > 0 && s > 0 -> "Таймер на $m мин $s сек"
                m > 0 -> "Таймер на $m мин"
                else -> "Таймер на $s сек"
            }
        } catch (e: Exception) {
            "Не получилось запустить таймер"
        }
    }

    private fun call(c: Context, t: String): String {
        val digits = t.filter { it.isDigit() }
        if (digits.length < 5) {
            return "Скажи номер цифрами, например: позвони 89001234567"
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            c.startActivity(intent)
            "Набираю $digits"
        } catch (e: Exception) {
            "Не получилось открыть набор номера"
        }
    }

    private fun openApp(c: Context, t: String): String {
        val name = t
            .removePrefix("включи приложение")
            .removePrefix("открой")
            .removePrefix("запусти")
            .replace("приложение", "")
            .trim()
        if (name.isBlank()) return "Скажи, какое приложение открыть"

        val aliases = mapOf(
            "ютуб" to "youtube",
            "youtube" to "youtube",
            "телеграм" to "telegram",
            "телеграмм" to "telegram",
            "вк" to "vk",
            "вконтакте" to "vk",
            "ватсап" to "whatsapp",
            "вотсап" to "whatsapp",
            "тикток" to "tiktok",
            "инстаграм" to "instagram",
            "камеру" to "camera",
            "камера" to "camera",
            "звонилку" to "dialer",
            "телефон" to "dialer",
            "настройки" to "settings",
            "галерею" to "gallery",
            "галерея" to "gallery",
            "музыку" to "music",
            "карты" to "maps"
        )
        val query = aliases[name] ?: name

        val pm = c.packageManager
        val apps = try {
            pm.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList()
        }

        val found = apps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale("ru"))
            val pkg = app.packageName.lowercase(Locale.ROOT)
            label.contains(query) || pkg.contains(query)
        } ?: return "Не нашёл приложение «$name»"

        val launch = pm.getLaunchIntentForPackage(found.packageName)
            ?: return "«$name» не открывается напрямую"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            c.startActivity(launch)
            "Открываю " + pm.getApplicationLabel(found).toString()
        } catch (e: Exception) {
            "Не получилось открыть «$name»"
        }
    }

    private fun volume(c: Context, up: Boolean): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            if (up) "Сделал громче" else "Сделал тише"
        } catch (e: Exception) {
            "Не получилось поменять громкость"
        }
    }

    private val numberWords = listOf(
        "ноль" to 0, "один" to 1, "одну" to 1, "одна" to 1,
        "два" to 2, "две" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13,
        "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16,
        "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19,
        "двадцать" to 20, "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50,
        "полчаса" to 30
    )

    /** Достаёт числа из фразы: цифрами или словами. */
    private fun numbers(t: String): List<Int> {
        val digits = Regex("\\d+").findAll(t).map { it.value.toInt() }.toList()
        if (digits.isNotEmpty()) return digits

        val out = ArrayList<Int>()
        var tens = -1
        val parts = t.replace(":", " ").split(' ', ',', '.', '-')
        for (p in parts) {
            val w = numberWords.firstOrNull { it.first == p }?.second ?: continue
            if (w == 20 || w == 30 || w == 40 || w == 50) {
                tens = w
            } else if (tens > 0 && w < 10) {
                out.add(tens + w)
                tens = -1
            } else {
                out.add(w)
            }
        }
        if (tens > 0) out.add(tens)
        return out
    }
}
