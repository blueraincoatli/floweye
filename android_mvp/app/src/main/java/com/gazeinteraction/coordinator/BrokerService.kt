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

    fun start(port: Int = 1883) {
        if (isRunning) return
        try {
            val props = Properties().apply {
                setProperty("host", "0.0.0.0")
                setProperty("port", port.toString())
                setProperty("allow_anonymous", "true")
            }
            server = Server()
            server?.startServer(MemoryConfig(props))
            isRunning = true
            Log.i(TAG, "MQTT Broker started on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Broker start failed", e)
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
