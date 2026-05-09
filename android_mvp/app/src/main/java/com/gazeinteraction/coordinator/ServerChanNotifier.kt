package com.gazeinteraction.coordinator

import android.util.Log
import kotlinx.coroutines.*
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ServerChanNotifier(private val sendKey: String?) : CaregiverNotifier {

    override val enabled = !sendKey.isNullOrBlank()

    companion object {
        private const val TAG = "ServerChanNotifier"
        private const val API_URL = "https://sctapi.ftqq.com"
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    private fun post(title: String, body: String): Boolean {
        if (!enabled) return false
        return try {
            val url = URL("$API_URL/$sendKey.send")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val formBody = "title=${URLEncoder.encode(title, "UTF-8")}" +
                "&desp=${URLEncoder.encode(body, "UTF-8")}"

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(formBody)
                writer.flush()
            }

            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.w(TAG, "ServerChan post failed: ${e.message}")
            false
        }
    }

    override fun sendEmergency(label: String) {
        scope.launch {
            post("患者紧急呼叫！",
                "## 紧急呼叫\n\n患者请求：**$label**\n\n请尽快前往查看。")
        }
    }

    override fun sendImportant(label: String) {
        scope.launch {
            post("患者请求帮助",
                "### 患者需求\n\n**$label**\n\n请及时处理。")
        }
    }

    override fun sendNormal(label: String) {
        scope.launch {
            post("患者消息", "患者选择了「$label」")
        }
    }

    override fun sendTest(callback: (Boolean) -> Unit) {
        if (!enabled) {
            callback(false)
            return
        }
        scope.launch {
            val ok = post("Floweye 连接测试", "通知功能正常工作")
            withContext(Dispatchers.Main) { callback(ok) }
        }
    }
}
