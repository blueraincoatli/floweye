package com.gazeinteraction.mqtt

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * MQTT客户端管理类
 *
 * 功能：
 * 1. 连接到MQTT Broker（支持自动重连）
 * 2. 发布视线状态数据
 * 3. 订阅协调器决策话题
 * 4. 重连后自动重新订阅
 */
class MqttClient(
    private val context: Context,
    private val deviceId: String
) {

    companion object {
        private const val TAG = "MqttClient"
        private const val PREFS_NAME = "gaze_mqtt_prefs"
        private const val PREF_KEY_BROKER_HOST = "broker_host"
        private const val PREF_KEY_BROKER_PORT = "broker_port"

        // 默认值（仅作为首次使用时的回退）
        private const val DEFAULT_BROKER_HOST = "127.0.0.1"
        private const val DEFAULT_BROKER_PORT = 1883
        private const val QOS = 1
        private const val KEEP_ALIVE_INTERVAL = 60
        private const val CONNECTION_TIMEOUT = 30

        // 主题配置
        private const val TOPIC_PREFIX = "gazecontrol"
        private const val GAZE_STATUS_TOPIC = "$TOPIC_PREFIX/device/%s/gaze_status"
        private const val DEVICE_STATUS_TOPIC = "$TOPIC_PREFIX/device/%s/status"
        private const val COORDINATION_TOPIC = "$TOPIC_PREFIX/coordination/decision"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var pahoClient: org.eclipse.paho.client.mqttv3.MqttClient? = null
    private val gson = Gson()

    // 连接参数
    private var brokerHost = prefs.getString(PREF_KEY_BROKER_HOST, DEFAULT_BROKER_HOST) ?: DEFAULT_BROKER_HOST
    private var brokerPort = prefs.getInt(PREF_KEY_BROKER_PORT, DEFAULT_BROKER_PORT)
    @Volatile
    private var isConnected = false
    private var intentionalDisconnect = false

    // 监听器
    var connectionListener: ConnectionListener? = null

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onConnectionFailed(error: String)
        fun onMessageReceived(topic: String, message: String)
    }

    /**
     * 连接MQTT Broker
     */
    fun connect(host: String? = null, port: Int? = null) {
        brokerHost = host ?: prefs.getString(PREF_KEY_BROKER_HOST, DEFAULT_BROKER_HOST) ?: DEFAULT_BROKER_HOST
        brokerPort = port ?: prefs.getInt(PREF_KEY_BROKER_PORT, DEFAULT_BROKER_PORT)
        intentionalDisconnect = false

        Thread {
            try {
                val serverUri = "tcp://$brokerHost:$brokerPort"
                val clientId = "GazeApp_$deviceId"
                Log.i(TAG, "连接MQTT Broker: $serverUri, ClientId: $clientId")

                pahoClient = org.eclipse.paho.client.mqttv3.MqttClient(
                    serverUri, clientId, MemoryPersistence()
                )

                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = true
                    isCleanSession = true
                    keepAliveInterval = KEEP_ALIVE_INTERVAL
                    connectionTimeout = CONNECTION_TIMEOUT
                    val willTopic = String.format(DEVICE_STATUS_TOPIC, deviceId)
                    val willMessage = gson.toJson(mapOf(
                        "deviceId" to deviceId,
                        "status" to "offline",
                        "timestamp" to System.currentTimeMillis()
                    ))
                    setWill(willTopic, willMessage.toByteArray(), QOS, true)
                }

                // 使用 MqttCallbackExtended，重连后自动重新订阅
                pahoClient?.setCallback(object : MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        isConnected = true
                        Log.i(TAG, "MQTT连接成功 (reconnect=%b): %s".format(reconnect, serverURI))
                        publishDeviceStatus("online")
                        subscribeToCoordinationTopic()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            connectionListener?.onConnected()
                        }
                    }

                    override fun connectionLost(cause: Throwable?) {
                        isConnected = false
                        Log.w(TAG, "MQTT连接丢失", cause)
                        if (!intentionalDisconnect) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                connectionListener?.onDisconnected()
                            }
                        }
                    }

                    override fun messageArrived(topic: String, message: MqttMessage) {
                        val payload = String(message.payload)
                        Log.d(TAG, "收到MQTT消息: $topic -> $payload")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            connectionListener?.onMessageReceived(topic, payload)
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken) {}
                })

                pahoClient?.connect(options)
                // 首次连接成功时 connectComplete 也会被调用，不需要重复处理

            } catch (e: Exception) {
                Log.e(TAG, "MQTT连接失败", e)
                isConnected = false
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    connectionListener?.onConnectionFailed(e.message ?: "连接失败")
                }
            }
        }.start()
    }

    /**
     * 发布视线状态数据
     */
    fun publishGazeState(gazeData: Map<String, Any>) {
        if (!isConnected) {
            Log.w(TAG, "MQTT未连接，无法发布视线状态")
            return
        }
        try {
            val topic = String.format(GAZE_STATUS_TOPIC, deviceId)
            val message = gson.toJson(gazeData)
            pahoClient?.publish(topic, message.toByteArray(), QOS, false)
        } catch (e: Exception) {
            Log.e(TAG, "视线状态发布失败", e)
        }
    }

    /**
     * 发布设备状态
     */
    private fun publishDeviceStatus(status: String) {
        try {
            val topic = String.format(DEVICE_STATUS_TOPIC, deviceId)
            val statusData = mapOf(
                "deviceId" to deviceId,
                "status" to status,
                "timestamp" to System.currentTimeMillis(),
                "capabilities" to listOf("gaze_detection", "yes_no_interaction")
            )
            val message = gson.toJson(statusData)
            pahoClient?.publish(topic, message.toByteArray(), QOS, true)
            Log.d(TAG, "设备状态发布成功: $status")
        } catch (e: Exception) {
            Log.e(TAG, "发布设备状态异常", e)
        }
    }

    /**
     * 订阅协调话题
     */
    private fun subscribeToCoordinationTopic() {
        try {
            pahoClient?.subscribe(COORDINATION_TOPIC, QOS)
            Log.i(TAG, "成功订阅协调话题: $COORDINATION_TOPIC")
        } catch (e: Exception) {
            Log.e(TAG, "订阅协调话题失败", e)
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        intentionalDisconnect = true
        try {
            if (isConnected) {
                publishDeviceStatus("offline")
                isConnected = false
                pahoClient?.disconnect()
                pahoClient?.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "断开MQTT连接异常", e)
        }
    }

    fun isConnected(): Boolean = isConnected

    fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "brokerHost" to brokerHost,
            "brokerPort" to brokerPort,
            "deviceId" to deviceId,
            "isConnected" to isConnected,
            "clientId" to "GazeApp_$deviceId"
        )
    }

    fun updateBrokerAddress(host: String, port: Int) {
        if (host != brokerHost || port != brokerPort) {
            Log.i(TAG, "更新MQTT Broker地址: $host:$port")
            prefs.edit()
                .putString(PREF_KEY_BROKER_HOST, host)
                .putInt(PREF_KEY_BROKER_PORT, port)
                .apply()
            disconnect()
            connect(host, port)
        }
    }

    fun sendTestMessage() {
        val testData = mapOf(
            "deviceId" to deviceId,
            "messageType" to "test",
            "timestamp" to System.currentTimeMillis(),
            "message" to "测试消息"
        )
        publishGazeState(testData)
    }
}
