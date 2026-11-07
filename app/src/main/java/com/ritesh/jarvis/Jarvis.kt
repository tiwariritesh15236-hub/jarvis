package com.ritesh.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * JARVIS ka dimaag (poore app mein ek hi). Awaaz service, screen, call aur message sab yahin se kaam lete hain.
 * Jo kaam sach mein hua, wahi TaskLog mein jaata hai.
 */
object Jarvis {
    lateinit var app: Context
    lateinit var router: CommandRouter
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var inited = false
    private val main = Handler(Looper.getMainLooper())
    private val queued = ArrayList<String>()
    private var awakeUntil = 0L

    @Volatile var speaking = false
    @Volatile var listening = false
    var handsFree = true
    var wakeOnly = true
    var announceMessages = true
    var announceBody = false
    var announceCalls = true
    var secret = ""
    var assistantName = "जारविस"
    var pin = ""
    var unlocked = false
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
        wakeOnly = prefs().getBoolean("wakeonly", true)
        announceMessages = prefs().getBoolean("announce_msg", true)
        announceBody = prefs().getBoolean("announce_body", false)
        announceCalls = prefs().getBoolean("announce_call", true)
        secret = prefs().getString("secret", "") ?: ""
        assistantName = (prefs().getString("name", "") ?: "").ifBlank { "जारविस" }
        pin = prefs().getString("pin", "") ?: ""
        unlocked = pin.isBlank()
        tts = TextToSpeech(app) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { endSpeaking() }
                    override fun onError(id: String?) { endSpeaking() }
                })
                ttsReady = true
                main.post {
                    val q = ArrayList(queued)
                    queued.clear()
                    for (t in q) speak(t)
                }
            }
        }
    }

    // ---------- internet / contact ----------

    fun online(): Boolean {
        return try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return false
            val c = cm.getNetworkCapabilities(n) ?: return false
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    fun contactName(number: String?): String? {
        if (number.isNullOrBlank()) return null
        if (app.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            app.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    // ---------- settings ----------

    fun updateHandsFree(v: Boolean) {
        handsFree = v
        prefs().edit().putBoolean("handsfree", v).apply()
        if (v) VoiceService.start(app) else VoiceService.instance?.pause()
        onState?.invoke()
    }

    fun updateWakeOnly(v: Boolean) {
        wakeOnly = v
        prefs().edit().putBoolean("wakeonly", v).apply()
    }

    fun updateFlag(key: String, v: Boolean) {
        when (key) {
            "announce_msg" -> announceMessages = v
            "announce_body" -> announceBody = v
            "announce_call" -> announceCalls = v
        }
        prefs().edit().putBoolean(key, v).apply()
    }

    fun updateSecret(s: String) {
        secret = s.trim()
        prefs().edit().putString("secret", secret).apply()
    }

    fun updateName(s: String) {
        assistantName = s.trim().ifBlank { "जारविस" }
        prefs().edit().putString("name", assistantName).apply()
    }

    fun updatePin(s: String) {
        pin = s.trim()
        prefs().edit().putString("pin", pin).apply()
        if (pin.isBlank()) unlocked = true
    }

    fun tryUnlock(s: String): Boolean {
        if (pin.isBlank() || s.trim() == pin) { unlocked = true; return true }
        return false
    }

    // ---------- bolna ----------

    private fun endSpeaking() {
        // aawaz ki poonchh (echo) na sune isliye thoda ruk kar
        main.postDelayed({
            speaking = false
            onState?.invoke()
        }, 600)
    }

    fun speak(text: String) {
        if (!ttsReady) { queued.add(text); return }
        speaking = true
        VoiceService.instance?.pause()
        onState?.invoke()
        val r = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "j")
        if (r != TextToSpeech.SUCCESS) { speaking = false; onState?.invoke() }
        // agar kisi wajah se "done" na aaye to atak na jaye
        main.postDelayed({ if (speaking) { speaking = false; onState?.invoke() } }, 20000)
    }

    /** Bahar se aayi khabar (message, phone). Isme "jarvis" kehna zaroori nahi. */
    fun announce(text: String, agent: String = "inbox") {
        main.post {
            TaskLog.add(agent, text, TaskLog.DONE)
            pending.add(Job(agent, text, true))
            reply = CommandRouter.Reply(agent, text)
            speak(text)
            onEvent?.invoke()
        }
    }

    // ---------- hukm ----------

    private val defaultWake = Regex(
        "^\\W*(hey |hi |ok |okay |ओके |हे |अरे )?(jarvis|jarvish|jarwis|jarvice|जारविस|जार्विस|जर्विस|जारवीस|जारविश|जार्वीस|जरविस|जारवि)[,.!? ]*",
        RegexOption.IGNORE_CASE
    )

    /** User ne jo naam rakha hai, usi se pehchane. Default "जारविस"/"jarvis" ho to purane spellings bhi chalte hain. */
    private fun wake(): Regex {
        val n = assistantName.trim()
        if (n.isBlank() || n == "जारविस" || n.equals("jarvis", true)) return defaultWake
        return Regex("^\\W*(hey |hi |ok |okay |ओके |हे |अरे )?" + Regex.escape(n) + "[,.!? ]*", RegexOption.IGNORE_CASE)
    }

    private val questionWord = Regex("क्या|कौन|कैसे|क्यों|कब|कहाँ|कहां|कितना|कितने|कितनी|समझाओ|मतलब|बारे में|\\bwhat\\b|\\bwho\\b|\\bhow\\b|\\bwhy\\b|\\bwhen\\b|\\bwhere\\b|explain|tell me")
    private val localWord = Regex("समय|बैटरी|battery|तारीख|alarm|अलार्म|टाइमर|torch|टॉर्च|volume|आवाज|याद|नोट|note|screenshot|स्क्रीनशॉट|स्क्रॉल|call|कॉल|मैसेज|रास्ता|गाना|यूट्यूब|खोल|kholo|game|गेम|खेल")

    private fun looksLikeQuestion(q: String): Boolean =
        q.split(Regex("\\s+")).size >= 4 && questionWord.containsMatchIn(q) && !localWord.containsMatchIn(q)

    private val companionWord = Regex(
        "साथी|मेरा दोस्त|दिल की बात|मन की बात|मन उदास|मन दुखी|उदास हूँ|अकेला महसूस|अकेलापन|परेशान हूँ|tension|टेंशन|stress|स्ट्रेस|दुखी हूँ|कोई नहीं सुनता|बात करनी है|बात करो ना",
        RegexOption.IGNORE_CASE
    )
    private val researchWord = Regex("रिसर्च|research|गहराई से|खोजकर बताओ|विस्तार से बताओ|पूरी जानकारी दो", RegexOption.IGNORE_CASE)
    private val designWord = Regex("डिज़ाइन आइडिया|डिजाइन आइडिया|डिज़ाइन बताओ|डिजाइन बताओ|लोगो आइडिया|poster idea|design idea|logo idea", RegexOption.IGNORE_CASE)
    private val marketingWord = Regex("मार्केटिंग|marketing|विज्ञापन लिखो|ad likho|कैप्शन लिखो|caption likho|पोस्ट लिखो", RegexOption.IGNORE_CASE)

    private val talkAgents = setOf("companion", "research", "design", "marketing")

    /** Inme se koi mode mile to Brain seedhe baat/draft deta hai, phone-kaam nahi chalata. */
    private fun talkMode(q: String): String? = when {
        companionWord.containsMatchIn(q) -> "companion"
        researchWord.containsMatchIn(q) -> "research"
        designWord.containsMatchIn(q) -> "design"
        marketingWord.containsMatchIn(q) -> "marketing"
        else -> null
    }

    private val modeLabel = mapOf(
        "companion" to "बात करने के लिए",
        "research" to "रिसर्च के लिए",
        "design" to "डिज़ाइन आइडिया के लिए",
        "marketing" to "मार्केटिंग ड्राफ़्ट के लिए"
    )

    fun run(raw: String, fromVoice: Boolean = false) {
        var cmd = raw.trim()
        if (cmd.isBlank()) return
        val now = System.currentTimeMillis()
        val w = wake()
        val hadWake = w.containsMatchIn(cmd)
        cmd = cmd.replace(w, "").trim()
        val awake = now < awakeUntil

        // chup rehna: bina "jarvis" kahe kuch nahi karta, kuch nahi bolta
        if (fromVoice && wakeOnly && !hadWake && !awake) return
        if (fromVoice && secret.isNotBlank() && !awake) {
            if (!cmd.contains(secret, ignoreCase = true)) return
            cmd = cmd.replace(secret, "", ignoreCase = true).trim()
        }
        awakeUntil = now + 10000

        if (cmd.isBlank()) {
            heard = assistantName
            val r = CommandRouter.Reply("manager", "जी, बोलिए।")
            reply = r
            speak(r.text)
            onEvent?.invoke()
            return
        }
        heard = cmd
        val speakUnknown = !fromVoice || hadWake || awake
        val q = cmd.lowercase(Locale.getDefault())

        val mode = talkMode(q)
        if (mode != null) {
            val label = modeLabel[mode] ?: "इसके लिए"
            if (!Brain.ready()) {
                finish(cmd, CommandRouter.Reply(mode, "$label सेटिंग में AI की चाबी डालिए।", false), speakUnknown)
                return
            }
            if (!online()) {
                finish(cmd, CommandRouter.Reply(mode, "इंटरनेट नहीं है, $label अभी नहीं हो पाएगा।", false), speakUnknown)
                return
            }
            Brain.askMode(cmd, mode) { res -> onBrain(cmd, res, mode) }
            return
        }

        val brainFirst = Brain.ready() && looksLikeQuestion(q)

        var local: CommandRouter.Reply? = null
        if (!brainFirst) {
            val r = router.handle(cmd)
            if (!r.unknown) { finish(cmd, r, true); return }
            local = r
        }
        if (!Brain.ready()) {
            val r = CommandRouter.Reply("manager", "यह काम मुझे अभी नहीं आता। सवाल-जवाब के लिए सेटिंग में AI की चाबी डालिए।", false, true)
            finish(cmd, local?.let { r } ?: r, speakUnknown)
            return
        }
        if (!online()) {
            finish(cmd, CommandRouter.Reply("ai", "इंटरनेट नहीं है, इसलिए सवाल-जवाब अभी नहीं हो सकते। फ़ोन के काम ऑफ़लाइन चलते हैं।", false), speakUnknown)
            return
        }
        Brain.ask(cmd) { res -> onBrain(cmd, res) }
    }

    private fun onBrain(cmd: String, res: Brain.Result, agent: String = "ai") {
        if (!res.ok) { finish(cmd, CommandRouter.Reply(agent, res.say, false), true); return }
        if (agent in talkAgents) {
            val say = res.say.ifBlank { "ठीक है।" }
            TaskLog.add(agent, "$cmd → $say", TaskLog.DONE)
            pending.add(Job(agent, cmd, true))
            reply = CommandRouter.Reply(agent, say)
            speak(say)
            onEvent?.invoke()
            return
        }
        val outs = ArrayList<CommandRouter.Reply>()
        for (c in res.cmds.take(5)) {
            val rr = router.handle(c)
            if (!rr.unknown) {
                outs.add(rr)
                TaskLog.add(rr.agent, "$c → ${rr.text}", if (rr.ok) TaskLog.DONE else TaskLog.FAILED)
                pending.add(Job(rr.agent, c, rr.ok))
            }
        }
        val failed = outs.filter { !it.ok }.joinToString(" ") { it.text }
        var say = res.say.ifBlank { outs.joinToString(" ") { it.text } }
        if (failed.isNotBlank() && res.say.isNotBlank()) say = say + " " + failed
        if (say.isBlank()) say = "ठीक है।"
        val ok = outs.all { it.ok }
        TaskLog.add("ai", "$cmd → $say", if (ok) TaskLog.DONE else TaskLog.FAILED)
        pending.add(Job("ai", cmd, ok))
        reply = CommandRouter.Reply("ai", say, ok)
        speak(say)
        onEvent?.invoke()
    }

    private fun finish(cmd: String, r: CommandRouter.Reply, speakIt: Boolean) {
        reply = r
        TaskLog.add(r.agent, "$cmd → ${r.text}", if (r.ok) TaskLog.DONE else TaskLog.FAILED)
        pending.add(Job(r.agent, cmd, r.ok))
        if (speakIt) speak(r.text)
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
