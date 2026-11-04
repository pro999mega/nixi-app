package com.nagibatorz.nixi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class ScreenActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE = 401
        private const val VISION_PROMPT =
            "Ты Никси, ассистент, который смотрит на экран телефона. Опиши по-русски, что на экране: " +
                "какое это приложение или сайт, что происходит и что стоит сделать. Очень коротко, одно-три предложения, " +
                "без markdown, потому что ответ читается вслух."
    }

    private val handler = Handler(Looper.getMainLooper())

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) {
            finish()
            return
        }
        if (resultCode != Activity.RESULT_OK || data == null) {
            Bus.emit("Ты не дал доступ к экрану, поэтому посмотреть не получилось")
            finish()
            return
        }
        captureAndAsk(resultCode, data)
    }

    @Suppress("DEPRECATION")
    private fun captureAndAsk(resultCode: Int, data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection: MediaProjection? = manager.getMediaProjection(resultCode, data)
        if (projection == null) {
            Bus.emit("Не удалось получить доступ к экрану")
            finish()
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val virtualDisplay = projection.createVirtualDisplay(
            "nixi-screen",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = Unit
        }, handler)

        handler.postDelayed({
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image == null) {
                    Bus.emit("Не получилось снять экран, попробуй ещё раз")
                    finish()
                } else {
                    val full = toBitmap(image, width, height)
                    val small = scaleDown(full, 1024)
                    val out = ByteArrayOutputStream()
                    small.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    askVision(base64)
                }
            } catch (t: Throwable) {
                Bus.emit("Ошибка захвата экрана: ${t.message ?: "неизвестно"}")
                finish()
            } finally {
                image?.close()
                virtualDisplay.release()
                reader.close()
                projection.stop()
            }
        }, 900)
    }

    private fun toBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val padded = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(padded, 0, 0, width, height)
    }

    private fun scaleDown(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val ratio = maxWidth.toFloat() / source.width
        return Bitmap.createScaledBitmap(source, maxWidth, (source.height * ratio).toInt(), true)
    }

    private fun askVision(base64: String) {
        val key = Prefs.apiKey(this)
        if (key.isBlank()) {
            Bus.emit("Сначала впиши ключ OpenRouter в приложении Nixi")
            finish()
            return
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", VISION_PROMPT))
            val parts = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", "Что на экране? Разбери коротко."))
                put(
                    JSONObject()
                        .put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64"))
                )
            }
            put(JSONObject().put("role", "user").put("content", parts))
        }

        val model = Prefs.visionModel(this)
        Thread {
            val answer = AiClient.chat(key, model, messages)
            handler.post {
                Bus.emit(answer)
                finish()
            }
        }.start()
    }
}
