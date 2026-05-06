package com.gazeinteraction.coordinator

import android.util.Log
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import java.util.*

class BrokerService {
    companion object {
        private const val TAG = "BrokerService"
    }

    private var server: Server? = null
    var isRunning = false
        private set
    var lastError: String? = null
        private set

    fun start(context: android.content.Context, port: Int = 1883) {
        if (isRunning) return
        lastError = null
        try {
            val dataDir = context.filesDir.absolutePath + "/moquette"
            val props = Properties().apply {
                setProperty("host", "0.0.0.0")
                setProperty("port", port.toString())
                setProperty("allow_anonymous", "true")
                setProperty("data_path", dataDir)
            }
            java.io.File(dataDir).mkdirs()
            server = Server()
            server?.startServer(MemoryConfig(props))
            isRunning = true
            Log.i(TAG, "MQTT Broker started on port $port, data=$dataDir")
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, "Broker start failed: $lastError", e)
            isRunning = false
        }
    }

    fun stop() {
        try {
            server?.stopServer()
            isRunning = false
            Log.i(TAG, "MQTT Broker stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Broker stop failed", e)
        }
    }
}
