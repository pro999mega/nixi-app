package com.nagibatorz.nixi
​import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
​class MainActivity : AppCompatActivity() {
​private data class Message(val role: String, val text: String)
​private lateinit var drawerLayout: DrawerLayout
private lateinit var chatContainer: LinearLayout
private lateinit var chatScrollView: ScrollView
private lateinit var inputField: EditText
private lateinit var chatsListContainer: LinearLayout
​private val messages = mutableListOf<Message>()
private var typingIndicatorView: View? = nilView()
​private fun nilView(): View? = null
​override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
​val rootLayout = createRootLayout()
setContentView(rootLayout)
​loadHistoryIntoMemory()
renderChatMessages()
renderDrawerChats()
​checkApiKeyOnStart()
}
​private fun checkApiKeyOnStart() {
if (getApiKey().isEmpty()) {
Toast.makeText(this, "Добавь ключ OpenRouter в настройках", Toast.LENGTH_LONG).show()
openSettingsActivity()
}
}
​private fun getApiKey(): String {
val prefsNames = listOf("nixi_prefs", "nixi", "settings")
val keys = listOf("api_key", "openrouter_key", "key", "token")
for (name in prefsNames) {
val sp = getSharedPreferences(name, Context.MODE_PRIVATE)
for (key in keys) {
val value = sp.getString(key, null)
if (!value.isNullOrBlank()) {
return value.trim()
}
}
}
return ""
}
​private fun openSettingsActivity() {
runCatching {
startActivity(Intent(this, Class.forName("com.nagibatorz.nixi.SettingsActivity")))
}.onFailure {
Toast.makeText(this, "Экран настроек пока недоступен", Toast.LENGTH_SHORT).show()
}
}
​private fun startCapsuleService() {
runCatching {
startService(Intent().setClassName(packageName, "com.nagibatorz.nixi.CapsuleService"))
}.onFailure {
Toast.makeText(this, "Сервис капсулы пока недоступен", Toast.LENGTH_SHORT).show()
}
}
​private fun round(color: Int, radiusDp: Float, strokeWidthDp: Int, strokeColor: Int): GradientDrawable {
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
​private fun dp(value: Int): Int {
return (value * resources.displayMetrics.density).toInt()
}
​private fun createRootLayout(): View {
drawerLayout = DrawerLayout(this).apply {
layoutParams = ViewGroup.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.MATCH_PARENT
)
setBackgroundColor(Color.parseColor("#0B0B0D"))
}
​val mainContent = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
layoutParams = ViewGroup.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT,
ViewGroup.LayoutParams.MATCH_PARENT
)
setBackgroundColor(Color.parseColor("#0B0B0D"))
}
​// Header
val header = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(dp(16), dp(12), dp(16), dp(12))
background = round(Color.parseColor("#15151B"), 0f, 1, Color.parseColor("#25252E"))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
)
}
​val menuBtn = TextView(this).apply {
text = "☰"
textSize = 24f
setTextColor(Color.parseColor("#EDEDF1"))
setPadding(dp(4), dp(4), dp(12), dp(4))
setOnClickListener {
drawerLayout.openDrawer(GravityCompat.START)
}
}
​val titleView = TextView(this).apply {
text = "Никси"
textSize = 22f
setTypeface(null, Typeface.BOLD)
setTextColor(Color.parseColor("#FF7A18"))
gravity = Gravity.CENTER
layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
}
​val addChatBtn = TextView(this).apply {
text = "＋"
textSize = 26f
setTextColor(Color.parseColor("#EDEDF1"))
setPadding(dp(12), dp(4), dp(4), dp(4))
setOnClickListener {
clearCurrentChat()
}
}
​header.addView(menuBtn)
header.addView(titleView)
header.addView(addChatBtn)
mainContent.addView(header)
​// Chips strip
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
​chipsContainer.addView(createChip("Капсула") {
startCapsuleService()
})
chipsContainer.addView(createChip("Новый чат") {
clearCurrentChat()
})
chipsContainer.addView(createChip("Настройки") {
openSettingsActivity()
})
​chipsFrame.addView(chipsContainer)
chipsScroll.addView(chipsFrame)
mainContent.addView(chipsScroll)
​// Chat messages scroll
chatScrollView = ScrollView(this).apply {
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
0,
1f
)
isFillViewport = true
setPadding(dp(16), dp(8), dp(16), dp(8))
}
​val chatScrollFrame = FrameLayout(this).apply {
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.MATCH_PARENT
)
}
​chatContainer = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
layoutParams = FrameLayout.LayoutParams(
FrameLayout.LayoutParams.MATCH_PARENT,
FrameLayout.LayoutParams.WRAP_CONTENT
)
}
chatScrollFrame.addView(chatContainer)
chatScrollView.addView(chatScrollFrame)
mainContent.addView(chatScrollView)
​// Bottom input bar
val bottomBar = LinearLayout(this).apply {
orientation = LinearLayout.HORIZONTAL
gravity = Gravity.CENTER_VERTICAL
setPadding(dp(12), dp(10), dp(12), dp(10))
background = round(Color.parseColor("#15151B"), 0f, 1, Color.parseColor("#25252E"))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
)
}
​inputField = EditText(this).apply {
hint = "Сообщение…"
setHintTextColor(Color.parseColor("#8B8B95"))
setTextColor(Color.parseColor("#EDEDF1"))
textSize = 15f
background = round(Color.parseColor("#0B0B0D"), 22f, 1, Color.parseColor("#25252E"))
setPadding(dp(16), dp(10), dp(16), dp(10))
imeOptions = EditorInfo.IME_ACTION_SEND
inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
rightMargin = dp(10)
}
setOnEditorActionListener { _, actionId, event ->
if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
handleSendMessage()
true
} else {
false
}
}
}
​val sendBtn = ImageButton(this).apply {
setImageDrawable(androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_send))
background = round(Color.parseColor("#FF7A18"), 24f, 0, 0)
setPadding(dp(12), dp(12), dp(12), dp(12))
layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
setOnClickListener {
handleSendMessage()
}
}
​bottomBar.addView(inputField)
bottomBar.addView(sendBtn)
mainContent.addView(bottomBar)
​drawerLayout.addView(mainContent)
​// Side drawer view
val drawerView = createDrawerView()
val drawerParams = DrawerLayout.LayoutParams(
dp(300),
DrawerLayout.LayoutParams.MATCH_PARENT
).apply {
gravity = GravityCompat.START
}
drawerView.layoutParams = drawerParams
drawerLayout.addView(drawerView)
​return drawerLayout
}
​private fun createChip(text: String, onClick: () -> Unit): TextView {
return TextView(this).apply {
this.text = text
textSize = 13f
setTextColor(Color.parseColor("#EDEDF1"))
background = round(Color.parseColor("#15151B"), 18f, 1, Color.parseColor("#25252E"))
setPadding(dp(14), dp(8), dp(14), dp(8))
setOnClickListener { onClick() }
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.WRAP_CONTENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
rightMargin = dp(8)
}
}
}
​private fun createDrawerView(): View {
val layout = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setBackgroundColor(Color.parseColor("#15151B"))
setPadding(dp(16), dp(36), dp(16), dp(16))
isClickable = true
isFocusable = true
}
​val title = TextView(this).apply {
text = "Чаты"
textSize = 20f
setTypeface(null, Typeface.BOLD)
setTextColor(Color.parseColor("#EDEDF1"))
setPadding(0, 0, 0, dp(16))
}
layout.addView(title)
​val newChatBtn = TextView(this).apply {
text = "＋ Новый чат"
textSize = 15f
setTextColor(Color.parseColor("#FF7A18"))
background = round(Color.parseColor("#0B0B0D"), 16f, 1, Color.parseColor("#25252E"))
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
​val chatsScroll = ScrollView(this).apply {
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
​val settingsBtn = TextView(this).apply {
text = "⚙ Настройки"
textSize = 15f
setTextColor(Color.parseColor("#8B8B95"))
setPadding(dp(12), dp(14), dp(12), dp(14))
setOnClickListener {
drawerLayout.closeDrawer(GravityCompat.START)
openSettingsActivity()
}
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
topMargin = dp(12)
}
}
layout.addView(settingsBtn)
​return layout
}
​private fun renderDrawerChats() {
chatsListContainer.removeAllViews()
val sampleDialog = TextView(this).apply {
text = if (messages.isNotEmpty()) messages[0].text.take(24) + "..." else "Текущий диалог"
textSize = 14f
setTextColor(Color.parseColor("#EDEDF1"))
setPadding(dp(12), dp(12), dp(12), dp(12))
background = round(Color.parseColor("#0B0B0D"), 12f, 0, 0)
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.MATCH_PARENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
bottomMargin = dp(8)
}
}
chatsListContainer.addView(sampleDialog)
}
​private fun loadHistoryIntoMemory() {
messages.clear()
val sp = getSharedPreferences("nixi_history", Context.MODE_PRIVATE)
val jsonStr = sp.getString("messages", null) ?: return
try {
val arr = JSONArray(jsonStr)
for (i in 0 until arr.length()) {
val obj = arr.getJSONObject(i)
val role = obj.getString("role")
val text = obj.getString("text")
messages.add(Message(role, text))
}
} catch (e: Exception) {
// ignore corrupt history
}
}
​private fun saveHistoryFromMemory() {
val sp = getSharedPreferences("nixi_history", Context.MODE_PRIVATE)
val arr = JSONArray()
for (m in messages) {
val obj = JSONObject()
obj.put("role", m.role)
obj.put("text", m.text)
arr.put(obj)
}
sp.edit().putString("messages", arr.toString()).apply()
}
​private fun clearCurrentChat() {
messages.clear()
saveHistoryFromMemory()
renderChatMessages()
renderDrawerChats()
}
​private fun renderChatMessages() {
chatContainer.removeAllViews()
for (m in messages) {
addBubbleView(m.role, m.text, false)
}
if (typingIndicatorView != null) {
chatContainer.addView(typingIndicatorView)
}
scrollToBottom()
}
​private fun addBubbleView(role: String, text: String, animate: Boolean) {
val isUser = role == "user"
​val bubbleLayout = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(14), dp(12), dp(14), dp(12))
background = if (isUser) {
round(Color.parseColor("#FF7A18"), 20f, 0, 0)
} else {
round(Color.parseColor("#1A1A22"), 20f, 1, Color.parseColor("#25252E"))
}
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.WRAP_CONTENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
gravity = if (isUser) Gravity.END else Gravity.START
bottomMargin = dp(10)
}
}
​val tv = TextView(this).apply {
this.text = text
textSize = 15f
setTextColor(if (isUser) Color.parseColor("#101014") else Color.parseColor("#EDEDF1"))
maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
}
bubbleLayout.addView(tv)
​chatContainer.addView(bubbleLayout)
​if (animate) {
bubbleLayout.alpha = 0f
bubbleLayout.translationY = dp(15).toFloat()
bubbleLayout.animate().alpha(1f).translationY(0f).setDuration(250).start()
}
}
​private fun showTypingIndicator() {
if (typingIndicatorView != null) return
val bubbleLayout = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(14), dp(12), dp(14), dp(12))
background = round(Color.parseColor("#1A1A22"), 20f, 1, Color.parseColor("#25252E"))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.WRAP_CONTENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
gravity = Gravity.START
bottomMargin = dp(10)
}
}
​val tv = TextView(this).apply {
text = "печатает…"
textSize = 14f
setTextColor(Color.parseColor("#8B8B95"))
}
bubbleLayout.addView(tv)
typingIndicatorView = bubbleLayout
chatContainer.addView(bubbleLayout)
scrollToBottom()
}
​private fun removeTypingIndicator() {
val v = typingIndicatorView
if (v != null) {
chatContainer.removeView(v)
typingIndicatorView = null
}
}
​private fun scrollToBottom() {
chatScrollView.post {
chatScrollView.fullScroll(View.FOCUS_DOWN)
}
}
​private fun handleSendMessage() {
val text = inputField.text.toString().trim()
if (text.isEmpty()) return
inputField.setText("")
​messages.add(Message("user", text))
saveHistoryFromMemory()
addBubbleView("user", text, true)
scrollToBottom()
renderDrawerChats()
​showTypingIndicator()
​Thread {
callOpenRouterApi(text)
}.start()
}
​private fun callOpenRouterApi(userText: String) {
try {
val apiKey = getApiKey()
if (apiKey.isEmpty()) {
runOnUiThread {
removeTypingIndicator()
Toast.makeText(this, "Добавь ключ OpenRouter в настройках", Toast.LENGTH_LONG).show()
openSettingsActivity()
}
return
}
​val url = URL("https://openrouter.ai/api/v1/chat/completions")
val conn = url.openConnection() as HttpURLConnection
conn.requestMethod = "POST"
conn.setRequestProperty("Content-Type", "application/json")
conn.setRequestProperty("Authorization", "Bearer $apiKey")
conn.doOutput = true
conn.connectTimeout = 15000
conn.readTimeout = 15000
​val messagesArray = JSONArray()
val slice = messages.takeLast(12)
for (m in slice) {
val obj = JSONObject()
obj.put("role", m.role)
obj.put("content", m.text)
messagesArray.put(obj)
}
​val body = JSONObject()
body.put("model", "google/gemini-2.0-flash-001")
body.put("messages", messagesArray)
​conn.outputStream.use { os ->
val bytes = body.toString().toByteArray(Charsets.UTF_8)
os.write(bytes, 0, bytes.size)
}
​val code = conn.responseCode
val stream = if (code in 200..299) conn.inputStream else conn.errorStream
val responseStr = InputStreamReader(stream, Charsets.UTF_8).use { it.readText() }
​if (code in 200..299) {
val json = JSONObject(responseStr)
val choices = json.getJSONArray("choices")
if (choices.length() > 0) {
val messageObj = choices.getJSONObject(0).getJSONObject("message")
val content = messageObj.getString("content").trim()
runOnUiThread {
removeTypingIndicator()
messages.add(Message("assistant", content))
saveHistoryFromMemory()
addBubbleView("assistant", content, true)
scrollToBottom()
renderDrawerChats()
}
} else {
handleApiError()
}
} else {
handleApiError()
}
} catch (e: Exception) {
handleApiError()
}
}
​private fun handleApiError() {
runOnUiThread {
removeTypingIndicator()
val errorText = "Не удалось получить ответ"
messages.add(Message("assistant", errorText))
saveHistoryFromMemory()
​val bubbleLayout = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
setPadding(dp(14), dp(12), dp(14), dp(12))
background = round(Color.parseColor("#1A1A22"), 20f, 1, Color.parseColor("#25252E"))
layoutParams = LinearLayout.LayoutParams(
LinearLayout.LayoutParams.WRAP_CONTENT,
LinearLayout.LayoutParams.WRAP_CONTENT
).apply {
gravity = Gravity.START
bottomMargin = dp(10)
}
}
val tv = TextView(this).apply {
text = errorText
textSize = 15f
setTextColor(Color.parseColor("#8B8B95"))
}
bubbleLayout.addView(tv)
chatContainer.addView(bubbleLayout)
scrollToBottom()
}
}
}
