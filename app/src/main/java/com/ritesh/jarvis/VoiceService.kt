package com.ritesh.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Bina button dabaye hamesha sunta hai. Jab JARVIS bolta hai to sunna rok deta hai,
 * taaki apni hi awaaz ko hukm na samajh le.
 */
class VoiceService : Service() {

    companion object {
        @Volatile var instance: VoiceService? = null

        fun start(ctx: Context) {
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            try {
                ctx.startForegroundService(Intent(ctx, VoiceService::class.java))
            } catch (e: Exception) {
                // system ne abhi shuru karne nahi diya; app khulne par phir koshish hogi
            }
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var active = false
    private val startRun = Runnable { begin() }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Jarvis.init(applicationContext)
        val ch = NotificationChannel("jarvis_voice", "JARVIS आवाज़", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun notification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "jarvis_voice")
            .setContentTitle("JARVIS AI")
            .setContentText("सुन रहा हूँ — बिना बटन दबाए बोलिए")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification())
        }
        schedule(300)
        return START_STICKY
    }

    private fun schedule(ms: Long) {
        h.removeCallbacks(startRun)
        h.postDelayed(startRun, ms)
    }

    /** Bolte samay ya mic band hone par sunna rokta hai. */
    fun pause() {
        h.removeCallbacks(startRun)
        if (active) {
            try { rec?.cancel() } catch (e: Exception) {}
            active = false
        }
        Jarvis.listening = false
        schedule(800)
    }

    private fun muteBeep(mute: Boolean) {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val d = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, d, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, d, 0)
        } catch (e: Exception) {
            // Do Not Disturb mein system rokta hai; koi baat nahi
        }
    }

    private fun begin() {
        if (!Jarvis.handsFree) {
            Jarvis.listening = false
            Jarvis.onState?.invoke()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        if (Jarvis.speaking) { schedule(600); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Jarvis.listening = false
            Jarvis.onState?.invoke()
            schedule(5000)
            return
        }
        if (active) return
        if (rec == null) {
            rec = SpeechRecognizer.createSpeechRecognizer(this)
            rec?.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        muteBeep(true)
        try {
            rec?.startListening(i)
            active = true
            Jarvis.listening = true
            Jarvis.onState?.invoke()
        } catch (e: Exception) {
            active = false
            schedule(1500)
        }
        h.postDelayed({ muteBeep(false) }, 700)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(b: Bundle?) {}
        override fun onEvent(t: Int, b: Bundle?) {}

        override fun onResults(b: Bundle?) {
            active = false
            val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) Jarvis.run(text, true)
            schedule(400)
        }

        override fun onError(e: Int) {
            active = false
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { stopSelf(); return }
            if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || e == SpeechRecognizer.ERROR_CLIENT) {
                try { rec?.destroy() } catch (x: Exception) {}
                rec = null
            }
            val quiet = e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            schedule(if (quiet) 150 else 1200)
        }
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
        Jarvis.listening = false
        instance = null
        super.onDestroy()
    }
}
