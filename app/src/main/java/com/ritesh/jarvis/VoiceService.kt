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
    private var pendingStart = false
    private var cycles = 0
    private val startRun = Runnable { begin() }
    private val watchdogRun = Runnable { watchdog() }
    private val healthRun = Runnable { healthCheck() }

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
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, notification())
            }
        } catch (e: Exception) {
            // system ne abhi mic wali service shuru karne ki ijazat nahi di
            stopSelf()
            return START_NOT_STICKY
        }
        schedule(300)
        h.postDelayed(healthRun, 60_000)
        return START_STICKY
    }

    private fun schedule(ms: Long) {
        h.removeCallbacks(startRun)
        pendingStart = true
        h.postDelayed(startRun, ms)
    }

    /** Agar recognizer startListening ke baad 20 second tak chup-chaap atka reh jaaye
     * (kuch phone/OEM par ye bug hota hai), to use zabardasti todkar dobara shuru karte hain. */
    private fun armWatchdog() {
        h.removeCallbacks(watchdogRun)
        h.postDelayed(watchdogRun, 20_000)
    }

    private fun watchdog() {
        if (!active) return
        try { rec?.cancel() } catch (e: Exception) {}
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
        active = false
        Jarvis.partialText = ""
        schedule(300)
    }

    /** Har 1 minute mein check karta hai ki service chalu reh gayi par sunna band ho gaya
     * ho (kuch phone background mic silently rok dete hain). Har ~30 cycle par recognizer
     * engine bhi taaza bana dete hain, taaki lambi der chalne par engine kharab na ho. */
    private fun healthCheck() {
        h.postDelayed(healthRun, 60_000)
        if (!Jarvis.handsFree) return
        // na sunna chal raha hai, na dobara shuru hone wala hai — to zabardasti chalu karte hain
        if (!active && !pendingStart) schedule(300)
    }

    /** Bolte samay ya mic band hone par sunna rokta hai. */
    fun pause() {
        h.removeCallbacks(startRun)
        h.removeCallbacks(watchdogRun)
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
        pendingStart = false
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
        cycles++
        // kuch phone/OEM par recognizer engine lambi der chalne par sust/kharab ho jaata hai,
        // isliye har 25 cycle par naya bana dete hain, taaki sunna hamesha taaza bana rahe
        if (rec != null && cycles % 25 == 0) {
            try { rec?.destroy() } catch (e: Exception) {}
            rec = null
        }
        if (rec == null) {
            rec = SpeechRecognizer.createSpeechRecognizer(this)
            rec?.setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, !Jarvis.online())
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // dheere ya ruk-ruk kar bola hukm bhi poora sun le, jaldi na kaate, par 15 second
            // ki majboori na ho — pehle ye 15000 tha jisse chhota hukm bhi 15 second baad
            // hi process hota tha, isliye jawab "atka hua" lagta tha
            .putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 700)
            .putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 700)
            .putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 800)
            .putExtra("android.speech.extra.DICTATION_MODE", true)
        muteBeep(true)
        try {
            rec?.startListening(i)
            active = true
            Jarvis.listening = true
            Jarvis.onState?.invoke()
            armWatchdog()
        } catch (e: Exception) {
            active = false
            // engine shayad kharab ho gaya; agli baar taaza banayenge
            try { rec?.destroy() } catch (x: Exception) {}
            rec = null
            schedule(1500)
        }
        h.postDelayed({ muteBeep(false) }, 700)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(p: Bundle?) { Jarvis.lastMicError = "" }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}

        // jo bol rahe hain wo jeete-ji screen par dikhta hai, isse pata chalta hai mic chal raha hai
        override fun onPartialResults(b: Bundle?) {
            val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) { Jarvis.partialText = t; Jarvis.onState?.invoke() }
        }

        override fun onEvent(t: Int, b: Bundle?) {}

        override fun onResults(b: Bundle?) {
            active = false
            h.removeCallbacks(watchdogRun)
            Jarvis.partialText = ""
            val cands = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: ArrayList()
            // jo candidate "jarvis"/naam se shuru hota hai use pehle lo, warna sabse behtar (pehla) candidate
            val text = cands.firstOrNull { Jarvis.hasWake(it) } ?: cands.firstOrNull()
            if (!text.isNullOrBlank()) Jarvis.run(text, true)
            schedule(400)
        }

        override fun onError(e: Int) {
            active = false
            h.removeCallbacks(watchdogRun)
            Jarvis.partialText = ""
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { stopSelf(); return }
            if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || e == SpeechRecognizer.ERROR_CLIENT) {
                try { rec?.destroy() } catch (x: Exception) {}
                rec = null
            }
            val quiet = e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (!quiet) {
                Jarvis.lastMicError = when (e) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "इंटरनेट धीमा है या नहीं है, इसलिए आवाज़ पहचानने में दिक्कत हो रही है।"
                    SpeechRecognizer.ERROR_AUDIO -> "माइक से आवाज़ नहीं मिल रही। कोई और ऐप माइक इस्तेमाल कर रहा हो सकता है।"
                    SpeechRecognizer.ERROR_SERVER -> "आवाज़ पहचानने वाले सर्वर में दिक्कत है।"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "आवाज़ पहचानने वाला अभी व्यस्त है, फिर कोशिश कर रहा हूँ।"
                    else -> "आवाज़ सुनने में दिक्कत आई (कोड $e), फिर कोशिश कर रहा हूँ।"
                }
                Jarvis.onState?.invoke()
            }
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
