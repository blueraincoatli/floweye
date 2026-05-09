package com.gazeinteraction.coordinator

interface CaregiverNotifier {
    val enabled: Boolean
    fun sendEmergency(label: String)
    fun sendImportant(label: String)
    fun sendNormal(label: String)
    fun sendTest(callback: (Boolean) -> Unit)
}
