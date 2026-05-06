package com.gazeinteraction.coordinator

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.lang.reflect.Method
import java.net.NetworkInterface

class HostManager(private val context: Context) {
    enum class Role { HOST, CLIENT, UNKNOWN }

    companion object {
        private const val TAG = "HostManager"
    }

    var role: Role = Role.UNKNOWN
        private set
    var forceHostMode: Boolean = false

    fun detectRole(): Role {
        if (forceHostMode) {
            role = Role.HOST
            return Role.HOST
        }

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi == null) {
            Log.w(TAG, "WifiManager not available")
            role = Role.UNKNOWN
            return Role.UNKNOWN
        }

        // Method 1: Check if WiFi AP (hotspot) is enabled via reflection
        val apEnabled = isWifiApEnabled(wifi)
        Log.i(TAG, "isWifiApEnabled=$apEnabled")

        if (apEnabled) {
            role = Role.HOST
            Log.i(TAG, "Detected HOST mode (WiFi AP enabled)")
            return Role.HOST
        }

        // Method 2: Check if connected to a hotspot network (192.168.43.x)
        val connInfo = wifi.connectionInfo
        val ip = connInfo.ipAddress
        if (ip != 0) {
            val ipStr = String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                (ip shr 8) and 0xff,
                (ip shr 16) and 0xff,
                (ip shr 24) and 0xff
            )
            Log.i(TAG, "WiFi IP: $ipStr")
            if (ipStr.startsWith("192.168.43.")) {
                role = Role.CLIENT
                Log.i(TAG, "Detected CLIENT mode (hotspot network)")
                return Role.CLIENT
            }
        }

        // Method 3: Check network interfaces for hotspot IP (192.168.43.1)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val hostAddr = addr.hostAddress
                    if (hostAddr != null && hostAddr.startsWith("192.168.43.")) {
                        // This device has an IP in the hotspot range — likely the host
                        Log.i(TAG, "Found hotspot IP on interface ${ni.name}: $hostAddr")
                        if (hostAddr == "192.168.43.1") {
                            role = Role.HOST
                            Log.i(TAG, "Detected HOST mode (hotspot IP on interface)")
                            return Role.HOST
                        } else {
                            role = Role.CLIENT
                            Log.i(TAG, "Detected CLIENT mode (client IP on hotspot)")
                            return Role.CLIENT
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network interface scan failed", e)
        }

        Log.i(TAG, "Role detection result: UNKNOWN")
        role = Role.UNKNOWN
        return Role.UNKNOWN
    }

    private fun isWifiApEnabled(wifi: WifiManager): Boolean {
        return try {
            val method: Method = wifi.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            val result = method.invoke(wifi) as? Boolean ?: false
            Log.d(TAG, "isWifiApEnabled() returned: $result")
            result
        } catch (e: NoSuchMethodException) {
            Log.d(TAG, "isWifiApEnabled not available on this device")
            false
        } catch (e: Exception) {
            Log.d(TAG, "isWifiApEnabled reflection failed: ${e.message}")
            false
        }
    }

    fun getBrokerHost(): String = when (role) {
        Role.HOST -> "127.0.0.1"
        Role.CLIENT -> "192.168.43.1"
        Role.UNKNOWN -> "127.0.0.1"
    }

    fun getBrokerPort(): Int = 1883
}
