package com.gazeinteraction.coordinator

class CompositeNotifier(private val channels: List<CaregiverNotifier>) : CaregiverNotifier {

    override val enabled: Boolean get() = channels.any { it.enabled }

    override fun sendEmergency(label: String) {
        channels.forEach { it.sendEmergency(label) }
    }

    override fun sendImportant(label: String) {
        channels.forEach { it.sendImportant(label) }
    }

    override fun sendNormal(label: String) {
        channels.forEach { it.sendNormal(label) }
    }

    override fun sendTest(callback: (Boolean) -> Unit) {
        val results = mutableListOf<Boolean>()
        var pending = channels.size
        if (pending == 0) {
            callback(false)
            return
        }
        for (c in channels) {
            c.sendTest { ok ->
                synchronized(results) {
                    results.add(ok)
                    pending--
                    if (pending == 0) {
                        callback(results.any { it })
                    }
                }
            }
        }
    }
}
