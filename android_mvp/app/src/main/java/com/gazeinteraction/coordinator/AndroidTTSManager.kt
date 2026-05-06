package com.gazeinteraction.coordinator

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

class AndroidTTSManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var initDone = false
    private val pendingQueue = Collections.synchronizedList(mutableListOf<String>())
    private var isSpeaking = false

    fun initialize(callback: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            initDone = (status == TextToSpeech.SUCCESS)
            if (initDone) {
                tts?.language = Locale.CHINESE
                tts?.setSpeechRate(0.8f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        isSpeaking = false
                        processNext()
                    }
                    @Deprecated("")
                    override fun onError(id: String?) {
                        isSpeaking = false
                        processNext()
                    }
                })
                processNext()
            }
            callback(initDone)
        }
    }

    fun speak(text: String) {
        if (!initDone) {
            pendingQueue.add(text)
            return
        }
        if (isSpeaking) {
            pendingQueue.add(text)
            return
        }
        doSpeak(text)
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.stop()
        }
        isSpeaking = false
        pendingQueue.clear()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
    }

    private fun doSpeak(text: String) {
        isSpeaking = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.take(10))
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    private fun processNext() {
        val next = pendingQueue.removeFirstOrNull()
        if (next != null) {
            doSpeak(next)
        }
    }
}
