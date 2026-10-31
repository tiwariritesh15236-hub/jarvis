package com.ritesh.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * JARVIS ka dimaag (poore app mein ek hi). Awaaz service aur screen dono yahin se kaam lete hain.
 * Jo kaam sach mein hua, wahi TaskLog mein jaata hai.
 */
object Jarvis {
    lateinit var app: Context
    lateinit var router: CommandRouter
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var inited = false
    private val main = Handler(Looper.getMainLooper())

    @Volatile var speaking = false
    @Volatile var listening = false
    var handsFree = true
    var wakeOnly = false
    var heard = ""
    var reply: CommandRouter.Reply? = null

    class Job(val agent: String, val text: String, val ok: Boolean)
    val pending = ArrayList<Job>()

    var onEvent: (() -> Unit)? = null
    var onState: (() -> Unit)? = null

    private fun prefs() = app.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        router = CommandRouter(app)
        handsFree = prefs().getBoolean("handsfree", true)
        wakeOnly = prefs().getBoolean("wakeonly", false)
        tts = TextToSpeech(app) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { endSpeaking() }
                    override fun onError(id: String?) { endSpeaking() }
                })
                ttsReady = true
            }
        }
    }

    fun setHandsFree(v: Boolean) {
        handsFree = v
        prefs().edit().putBoolean("handsfree", v).apply()
        if (v) VoiceService.start(app) else VoiceService.instance?.pause()
        onState?.invoke()
    }

    fun setWakeOnly(v: Boolean) {
        wakeOnly = v
        prefs().edit().putBoolean("wakeonly", v).apply()
    }

    private fun endSpeaking() {
        // aawaz ki poonchh (echo) na sune isliye thoda ruk kar
        main.postDelayed({
            speaking = false
            onState?.invoke()
        }, 600)
    }

    fun speak(text: String) {
        if (!ttsReady) return
        speaking = true
        VoiceService.instance?.pause()
        onState?.invoke()
        val r = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j")
        if (r != TextToSpeech.SUCCESS) { speaking = false; onState?.invoke() }
        // agar kisi wajah se "done" na aaye to atak na jaye
        main.postDelayed({ if (speaking) { speaking = false; onState?.invoke() } }, 20000)
    }

    private val wake = Regex("^(hey |hi |ok |okay |ओके |हे )?(jarvis|jarvish|jarwis|जारविस|जार्विस|जर्विस|जारवि|जार्वि)[,.! ]*", RegexOption.IGNORE_CASE)

    fun run(raw: String, fromVoice: Boolean = false) {
        var cmd = raw.trim()
        if (cmd.isBlank()) return
        val hadWake = wake.containsMatchIn(cmd)
        cmd = cmd.replace(wake, "").trim()
        if (fromVoice && wakeOnly && !hadWake) return
        if (cmd.isBlank()) {
            heard = "जारविस"
            val r = CommandRouter.Reply("manager", "जी, बोलिए।")
            reply = r
            speak(r.text)
            onEvent?.invoke()
            return
        }
        heard = cmd
        val r = router.handle(cmd)
        reply = r
        TaskLog.add(r.agent, "$cmd → ${r.text}", if (r.ok) TaskLog.DONE else TaskLog.FAILED)
        pending.add(Job(r.agent, cmd, r.ok))
        // bina samjhi baat (aas-paas ki baatcheet) ko awaaz se baar-baar na dohraye
        if (!(fromVoice && r.unknown)) speak(r.text)
        onEvent?.invoke()
    }

    fun stopAll() {
        tts?.stop()
        speaking = false
        TaskLog.add("manager", "आपने इमरजेंसी स्टॉप दबाया। सब रुका।", TaskLog.STOPPED)
        pending.add(Job("__stop", "", true))
        onState?.invoke()
        onEvent?.invoke()
    }
}
