package com.gazeinteraction.coordinator

import android.util.Log
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TelegramNotifier(
    private val botToken: String?,
    private val chatId: String?
) : CaregiverNotifier {

    override val enabled = !botToken.isNullOrBlank() && !chatId.isNullOrBlank()

    companion object {
        private const val TAG = "TelegramNotifier"
        private const val API_URL = "https://api.telegram.org"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private fun post(text: String): Boolean {
        if (!enabled) return false
        return try {
            val url = URL("$API_URL/bot$botToken/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val json = """{"chat_id":"$chatId","text":${jsonQuote(text)},"parse_mode":"Markdown"}"""

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(json)
                writer.flush()
            }

            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.w(TAG, "Telegram post failed: ${e.message}")
            false
        }
    }

    private fun jsonQuote(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    override fun sendEmergency(label: String) {
        scope.launch {
            post("*患者紧急呼叫！*\n\n患者请求：_${escapeMd(label)}_\n\n请尽快前往查看。")
        }
    }

    override fun sendImportant(label: String) {
        scope.launch {
            post("*患者请求帮助*\n\n患者需求：_${escapeMd(label)}_\n\n请及时处理。")
        }
    }

    override fun sendNormal(label: String) {
        scope.launch {
            post("患者消息：患者选择了「$label」")
        }
    }

    override fun sendTest(callback: (Boolean) -> Unit) {
        if (!enabled) {
            callback(false)
            return
        }
        scope.launch {
            val ok = post("Floweye 连接测试：通知功能正常工作")
            withContext(Dispatchers.Main) { callback(ok) }
        }
    }

    private fun escapeMd(s: String): String =
        s.replace("_", "\\_").replace("*", "\\*")
            .replace("[", "\\[").replace("`", "\\`")
}
