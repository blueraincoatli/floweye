package com.gazeinteraction.coordinator

import android.content.Context
import android.net.wifi.WifiManager

class HostManager(private val context: Context) {
    enum class Role { HOST, CLIENT, UNKNOWN }

    var role: Role = Role.UNKNOWN
        private set

    fun detectRole(): Role {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi == null) return Role.UNKNOWN

        val dhcpInfo = wifi.dhcpInfo
        if (dhcpInfo != null && dhcpInfo.serverAddress == 0) {
            role = Role.HOST
            return Role.HOST
        }

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
            if (ipStr.startsWith("192.168.43.")) {
                role = Role.CLIENT
                return Role.CLIENT
            }
        }

        role = Role.UNKNOWN
        return Role.UNKNOWN
    }

    fun getBrokerHost(): String = when (role) {
        Role.HOST -> "127.0.0.1"
        Role.CLIENT -> "192.168.43.1"
        Role.UNKNOWN -> "127.0.0.1"
    }

    fun getBrokerPort(): Int = 1883
}
