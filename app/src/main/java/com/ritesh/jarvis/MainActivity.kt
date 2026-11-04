package com.ritesh.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var office: OfficeView

    private var tab = 0
    private var reactor: ReactorView? = null
    private var status: TextView? = null
    private var strip: TextView? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        Jarvis.init(this)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.NAV
        office = OfficeView(this)
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG
            ),
            1
        )

        val root = vbox().apply { setBackgroundColor(C.BG) }
        content = FrameLayout(this)
        root.addView(content, lp(MATCH, 0, 1f))
        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(C.NAV)
        }
        root.addView(nav, lp(MATCH, dp(64)))
        setContentView(root)
        show(0)
        startVoice()
    }

    override fun onRequestPermissionsResult(rq: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(rq, perms, res)
        startVoice()
        show(tab)
    }

    override fun onResume() {
        super.onResume()
        Jarvis.onEvent = { runOnUiThread { drain() } }
        Jarvis.onState = { runOnUiThread { updateState() } }
        startVoice()
        if (Jarvis.pin.isNotBlank() && !Jarvis.unlocked) {
            showLock()
        } else {
            drain()
            show(tab)
        }
    }

    override fun onPause() {
        if (Jarvis.pin.isNotBlank()) Jarvis.unlocked = false
        Jarvis.onEvent = null
        Jarvis.onState = null
        super.onPause()
    }

    private fun startVoice() {
        if (Jarvis.handsFree) VoiceService.start(this)
    }

    // ---------- taala (sirf app kholne ke liye) ----------

    private fun showLock() {
        content.removeAllViews()
        nav.removeAllViews()
        val col = page().apply { gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(ReactorView(this).apply { mode = 2 }, lpm(dp(140), dp(140), top = 40).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(label(Jarvis.assistantName, 22f, C.CYAN, true, true).apply { gravity = Gravity.CENTER }, lpm(MATCH, WRAP, top = 14))
        col.addView(label("यह सिर्फ़ आपके लिए है। PIN डालिए।", 14f, C.MUTED).apply { gravity = Gravity.CENTER }, lpm(MATCH, WRAP, top = 4))
        val pinF = field("PIN", "", true).apply {
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        col.addView(pinF, lpm(dp(220), dp(52), top = 24))
        val warn = label("", 13.5f, C.RED).apply { gravity = Gravity.CENTER }
        col.addView(warn, lpm(MATCH, WRAP, top = 8))
        col.addView(smallButton("खोलिए", C.CYAN) {
            if (Jarvis.tryUnlock(pinF.text.toString())) { drain(); show(tab) }
            else { warn.text = "PIN सही नहीं है।"; pinF.setText("") }
        }, lpm(dp(220), dp(48), top = 10))
        content.addView(ScrollView(this).apply { isFillViewport = true; addView(col) })
    }

    // ---------- jo kaam hua usko ofis mein dikhana ----------

    private fun drain() {
        val jobs = ArrayList(Jarvis.pending)
        Jarvis.pending.clear()
        for (j in jobs) {
            if (j.agent == "__stop") office.stopAll() else office.assign(j.agent, j.text, j.ok)
        }
        val locked = Jarvis.pin.isNotBlank() && !Jarvis.unlocked
        if (jobs.isNotEmpty() && tab != 2 && !locked) show(tab)
    }

    private fun stateText(): String = when {
        !Jarvis.handsFree -> "माइक बंद · चालू करने के लिए गोले को दबाइए"
        Jarvis.speaking -> "बोल रहा हूँ…"
        Jarvis.listening -> if (Jarvis.wakeOnly) "सुन रहा हूँ… पहले \"जारविस\" कहिए" else "सुन रहा हूँ… बिना बटन दबाए बोलिए"
        else -> "तैयार हो रहा हूँ…"
    }

    private fun stripText(): String {
        val live = Agents.list.count { it.live }
        val mic = when {
            !Jarvis.handsFree -> "OFF"
            Jarvis.speaking -> "SPEAKING"
            Jarvis.listening -> "LIVE"
            else -> "WAIT"
        }
        val net = if (Jarvis.online()) "ON" else "OFF"
        return "● JARVIS  ·  NET:$net  ·  MIC:$mic  ·  AGENTS:$live/${Agents.list.size}"
    }

    private fun updateState() {
        reactor?.mode = when {
            !Jarvis.handsFree -> 2
            Jarvis.speaking -> 1
            else -> 0
        }
        status?.text = stateText()
        strip?.text = stripText()
    }

    // ---------- tab ----------

    private fun show(i: Int) {
        tab = i
        content.removeAllViews()
        (office.parent as? ViewGroup)?.removeView(office)
        reactor = null
        status = null
        strip = null
        val v = when (i) {
            0 -> home()
            1 -> team()
            2 -> officeTab()
            3 -> tasks()
            else -> safety()
        }
        content.addView(ScrollView(this).apply { isFillViewport = true; addView(v) })
        buildNav()
    }

    private fun buildNav() {
        nav.removeAllViews()
        val names = listOf("होम", "टीम", "ऑफिस", "काम", "सेटिंग")
        for (i in names.indices) {
            nav.addView(TextView(this).apply {
                text = names[i]
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(if (i == tab) C.CYAN else C.MUTED)
                if (i == tab) setTypeface(typeface, Typeface.BOLD)
                setOnClickListener { show(i) }
            }, lp(0, MATCH, 1f))
        }
    }

    private fun page(): LinearLayout = vbox().apply { setPadding(dp(20), dp(28), dp(20), dp(16)) }

    private fun title(t: String, sub: String? = null): View = vbox().apply {
        addView(label(t, 28f, C.TEXT, true))
        if (sub != null) addView(label(sub, 14f, C.MUTED))
    }

    private fun stopButton(): Button = Button(this).apply {
        text = "■  इमरजेंसी स्टॉप · सब रोकें"
        textSize = 17f
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(C.ON_RED)
        background = shape(C.RED, 0, 16)
        setOnClickListener { Jarvis.stopAll() }
    }

    // ---------- 1. home ----------

    private fun home(): View {
        val col = page()

        val st = label(stripText(), 11.5f, C.GREEN, true, true)
        strip = st
        col.addView(st)
        col.addView(label(Jarvis.assistantName.uppercase(), 26f, C.CYAN, true, true).apply { letterSpacing = 0.25f }, lpm(MATCH, WRAP, top = 6))

        val r = ReactorView(this)
        reactor = r
        r.mode = when {
            !Jarvis.handsFree -> 2
            Jarvis.speaking -> 1
            else -> 0
        }
        r.contentDescription = "माइक चालू या बंद करने के लिए दबाइए"
        r.setOnClickListener {
            Jarvis.updateHandsFree(!Jarvis.handsFree)
            if (Jarvis.handsFree) startVoice()
            updateState()
        }
        col.addView(r, lpm(dp(250), dp(250), top = 10).apply { gravity = Gravity.CENTER_HORIZONTAL })

        val s2 = label(stateText(), 17f, C.TEXT, true).apply { gravity = Gravity.CENTER }
        status = s2
        col.addView(s2, lpm(MATCH, WRAP, top = 6))
        col.addView(label("पहले \"${Jarvis.assistantName}\" बोलिए, फिर हुक्म", 13.5f, C.MUTED).apply { gravity = Gravity.CENTER }, lpm(MATCH, WRAP, top = 2))

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            val warn = card(C.AMBER)
            warn.addView(label("इस फ़ोन में आवाज़ पहचानने वाला Google ऐप नहीं मिला। Play Store से Google ऐप इंस्टॉल या अपडेट कीजिए, तब बोलना चलेगा।", 14f, C.AMBER))
            col.addView(warn, lpm(MATCH, WRAP, top = 14))
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            val warn = card(C.RED)
            warn.addView(label("माइक की अनुमति नहीं है। फ़ोन की सेटिंग में JARVIS AI को माइक की अनुमति दीजिए।", 14f, C.RED))
            col.addView(warn, lpm(MATCH, WRAP, top = 14))
        }

        if (Jarvis.heard.isNotEmpty()) {
            val c1 = card()
            c1.addView(label("आपने कहा", 12.5f, C.MUTED))
            c1.addView(label("\"${Jarvis.heard}\"", 16f, C.TEXT))
            col.addView(c1, lpm(MATCH, WRAP, top = 16))
        }
        Jarvis.reply?.let { rp ->
            val c2 = card(if (rp.ok) C.AMBER else C.RED)
            c2.addView(label(Agents.nameOf(rp.agent), 12.5f, if (rp.ok) C.AMBER else C.RED, true))
            c2.addView(label(rp.text, 16f, C.TEXT))
            col.addView(c2, lpm(MATCH, WRAP, top = 12))
        }

        return col
    }

    // ---------- 2. team ----------

    private fun team(): View {
        val col = page()
        val needsAi = setOf("ai", "companion")
        val live = Agents.list.count { it.live && (it.id !in needsAi || Brain.ready()) }
        col.addView(title("मेरी टीम", "${Agents.list.size} एजेंट · $live तैयार · ${Agents.list.size - live} जल्द आएँगे"))

        val m = hbox().apply {
            background = shape(C.SURFACE, C.AMBER, 16, 2)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val mt = vbox()
        mt.addView(label("Manager", 18f, C.AMBER, true))
        mt.addView(label("हर हुक्म पहले यही समझता है और सही एजेंट को देता है", 13.5f, C.MUTED))
        m.addView(mt)
        col.addView(m, lpm(MATCH, WRAP, top = 14, bottom = 6))

        for (a in Agents.list) {
            val r = hbox().apply {
                background = shape(C.SURFACE, C.BORDER, 14)
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            val t = vbox()
            t.addView(label(a.name, 15.5f, C.TEXT, true))
            t.addView(label(a.desc, 12.5f, C.MUTED))
            r.addView(t, lp(0, WRAP, 1f))
            val ready = a.live && (a.id !in needsAi || Brain.ready())
            val word = if (ready) "तैयार" else if (a.live) "चाबी चाहिए" else "जल्द आएगा"
            r.addView(dot(if (ready) C.GREEN else if (a.live) C.AMBER else C.MUTED), lpm(dp(8), dp(8), left = 6))
            r.addView(label(word, 13f, if (ready) C.GREEN else if (a.live) C.AMBER else C.MUTED), lpm(WRAP, WRAP, left = 6))
            col.addView(r, lpm(MATCH, WRAP, top = 8))
        }
        return col
    }

    // ---------- ofis ----------

    private fun officeTab(): View {
        val col = page()
        col.addView(title("JARVIS ऑफिस", "Manager बुलाता है, एजेंट डायरी लेकर आता है, फिर अपनी सीट पर काम करता है"))
        col.addView(office, lpm(MATCH, dp(780), top = 14))
        col.addView(label("यहाँ सिर्फ़ असली काम दिखता है। बोलकर कोई हुक्म दीजिए, फिर यहाँ देखिए।", 13.5f, C.MUTED), lpm(MATCH, WRAP, top = 12))
        return col
    }

    // ---------- 3. kaam ----------

    private fun tasks(): View {
        val col = page()
        col.addView(title("लाइव काम", "जो सच में हुआ, वही यहाँ दिखता है"))

        if (TaskLog.entries.isEmpty()) {
            val c = card()
            c.addView(label("अभी कोई काम नहीं हुआ। बोलिए, फिर यहाँ दिखेगा।", 15f, C.MUTED))
            col.addView(c, lpm(MATCH, WRAP, top = 16))
        }
        for (e in TaskLog.entries.reversed()) {
            val color = when (e.state) {
                TaskLog.DONE -> C.GREEN
                TaskLog.FAILED -> C.RED
                TaskLog.STOPPED -> C.AMBER
                else -> C.CYAN
            }
            val word = when (e.state) {
                TaskLog.DONE -> "पूरा"
                TaskLog.FAILED -> "नहीं हो पाया"
                TaskLog.STOPPED -> "रुका"
                else -> "चल रहा है"
            }
            val c = card(color)
            c.addView(label("${e.time} · ${Agents.nameOf(e.agent)} · $word", 12.5f, color, true))
            c.addView(label(e.text, 15f, C.TEXT))
            col.addView(c, lpm(MATCH, WRAP, top = 10))
        }
        col.addView(stopButton(), lpm(MATCH, dp(56), top = 20))
        return col
    }

    // ---------- 4. setting ----------

    private fun toggleRaw(name: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val r = hbox().apply {
            background = shape(C.SURFACE, C.BORDER, 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val t = vbox()
        t.addView(label(name, 15.5f, C.TEXT, true))
        t.addView(label(sub, 12.5f, C.MUTED))
        val sw = Switch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList.valueOf(C.TEXT)
            trackTintList = ColorStateList.valueOf(if (checked) C.CYAN else C.BORDER)
            contentDescription = name
            setOnCheckedChangeListener { b, v ->
                (b as Switch).trackTintList = ColorStateList.valueOf(if (v) C.CYAN else C.BORDER)
                onChange(v)
            }
        }
        r.addView(t, lp(0, WRAP, 1f))
        r.addView(sw)
        return r
    }

    private fun toggle(key: String, name: String, sub: String): View =
        toggleRaw(name, sub, Jarvis.router.allowed(key)) { Jarvis.router.setAllowed(key, it) }

    private fun section(t: String): View =
        label(t, 13f, C.CYAN, true, true).apply { letterSpacing = 0.12f }

    private fun field(hintText: String, value: String, hidden: Boolean): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(C.MUTED)
        setTextColor(C.TEXT)
        textSize = 15f
        setText(value)
        inputType = if (hidden) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setSingleLine()
        background = shape(C.SURFACE, C.BORDER, 12)
        setPadding(dp(14), 0, dp(14), 0)
    }

    private fun smallButton(t: String, color: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setTextColor(C.ON_CYAN)
        background = shape(color, 0, 12)
        setOnClickListener { onClick() }
    }

    private fun listenerOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return s.contains(packageName)
    }

    private fun safety(): View {
        val col = page()
        col.addView(title("सेटिंग", "AI, आवाज़, खबरें और अनुमतियाँ"))

        // ----- naam aur pahunch -----
        col.addView(section("नाम और पहुँच"), lpm(MATCH, WRAP, top = 16))
        val nm = card()
        nm.addView(label("नाम", 15.5f, C.TEXT, true))
        nm.addView(label("इस नाम के बिना यह कुछ नहीं करेगा, न बोलेगा।", 12.5f, C.MUTED))
        val nameF = field("जैसे: जारविस", Jarvis.assistantName, false)
        nm.addView(nameF, lpm(MATCH, dp(46), top = 8))
        nm.addView(smallButton("नाम सेव करें", C.CYAN) {
            Jarvis.updateName(nameF.text.toString())
            Toast.makeText(this, "सेव हो गया", Toast.LENGTH_SHORT).show()
            show(4)
        }, lpm(MATCH, dp(44), top = 8))
        col.addView(nm, lpm(MATCH, WRAP, top = 8))

        val pinC = card()
        pinC.addView(label("PIN · सिर्फ़ आपकी पहुँच", 15.5f, C.TEXT, true))
        pinC.addView(label("PIN रखने पर ऐप खोलते ही PIN माँगेगा। खाली छोड़कर PIN हटा सकते हैं। यह फ़ोन में ही रहता है, कहीं भेजा नहीं जाता।", 12.5f, C.MUTED))
        val pinF2 = field("4 से 6 अंक", Jarvis.pin, true).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        pinC.addView(pinF2, lpm(MATCH, dp(46), top = 8))
        pinC.addView(smallButton("PIN सेव करें", C.CYAN) {
            Jarvis.updatePin(pinF2.text.toString())
            Toast.makeText(this, if (Jarvis.pin.isBlank()) "PIN हटा दिया" else "PIN सेव हो गया", Toast.LENGTH_SHORT).show()
        }, lpm(MATCH, dp(44), top = 8))
        col.addView(pinC, lpm(MATCH, WRAP, top = 8))

        // ----- AI -----
        col.addView(section("AI दिमाग़ · सवाल-जवाब"), lpm(MATCH, WRAP, top = 16))
        val ai = card()
        val on = Brain.ready()
        ai.addView(label(if (on) "✓ चालू: ${Brain.providerName()}" else "✗ बंद: AI की चाबी (API key) डालिए", 14.5f, if (on) C.GREEN else C.AMBER, true))
        ai.addView(label("चाबी सिर्फ़ इसी फ़ोन में रहती है। आपके सवाल चुनी हुई AI कंपनी के सर्वर पर जाते हैं और उसका खर्च आपके अकाउंट पर लगता है। इंटरनेट न हो तो सवाल-जवाब नहीं चलेंगे, फ़ोन के काम चलेंगे।", 12.5f, C.MUTED), lpm(MATCH, WRAP, top = 4))
        val prow = hbox()
        val provs = listOf("gemini" to "Gemini", "claude" to "Claude", "openai" to "ChatGPT")
        for ((id, nm) in provs) {
            val sel = Brain.provider() == id
            prow.addView(Button(this).apply {
                text = nm
                isAllCaps = false
                setTextColor(if (sel) C.ON_CYAN else C.TEXT)
                background = shape(if (sel) C.CYAN else C.SURFACE2, C.BORDER, 12)
                setOnClickListener { Brain.setProvider(id); show(4) }
            }, lpm(0, dp(44), left = 6, weight = 1f))
        }
        ai.addView(prow, lpm(MATCH, WRAP, top = 10))
        val keyF = field("API key यहाँ चिपकाइए", Brain.key(), true)
        val modelF = field("मॉडल का नाम", Brain.model(), false)
        ai.addView(keyF, lpm(MATCH, dp(46), top = 10))
        ai.addView(modelF, lpm(MATCH, dp(46), top = 8))
        ai.addView(smallButton("सेव करें", C.CYAN) {
            Brain.save(keyF.text.toString(), modelF.text.toString())
            Toast.makeText(this, "सेव हो गया। अब बोलिए: ${Jarvis.assistantName}, …", Toast.LENGTH_LONG).show()
            show(4)
        }, lpm(MATCH, dp(44), top = 10))
        col.addView(ai, lpm(MATCH, WRAP, top = 8))

        // ----- awaaz -----
        col.addView(section("आवाज़"), lpm(MATCH, WRAP, top = 18))
        col.addView(
            toggleRaw("हमेशा सुनो (बिना बटन)", "माइक चालू रहता है", Jarvis.handsFree) {
                Jarvis.updateHandsFree(it)
                if (it) startVoice()
            }, lpm(MATCH, WRAP, top = 8)
        )
        col.addView(
            toggleRaw("सिर्फ़ \"${Jarvis.assistantName}\" कहने पर काम करो", "बिना नाम लिए न कुछ करेगा, न बोलेगा", Jarvis.wakeOnly) {
                Jarvis.updateWakeOnly(it)
            }, lpm(MATCH, WRAP, top = 8)
        )
        val sec = card()
        sec.addView(label("गुप्त शब्द (ऐच्छिक)", 15.5f, C.TEXT, true))
        sec.addView(label("यह असली आवाज़ पहचान नहीं है। भरने पर \"${Jarvis.assistantName}\" के बाद यह शब्द बोले बिना काम नहीं होगा। जो आपका शब्द सुन ले, वह भी चला सकता है।", 12.5f, C.MUTED))
        val secF = field("जैसे: नीलकमल", Jarvis.secret, false)
        sec.addView(secF, lpm(MATCH, dp(46), top = 8))
        sec.addView(smallButton("शब्द सेव करें", C.CYAN) {
            Jarvis.updateSecret(secF.text.toString())
            Toast.makeText(this, "सेव हो गया", Toast.LENGTH_SHORT).show()
        }, lpm(MATCH, dp(44), top = 8))
        col.addView(sec, lpm(MATCH, WRAP, top = 8))

        // ----- khabar -----
        col.addView(section("खबरें · बिना \"जारविस\" कहे बोलेगा"), lpm(MATCH, WRAP, top = 18))
        col.addView(
            toggleRaw("आने वाले मैसेज की खबर", "\"X का मैसेज आया\" बोलेगा", Jarvis.announceMessages) { Jarvis.updateFlag("announce_msg", it) },
            lpm(MATCH, WRAP, top = 8)
        )
        col.addView(
            toggleRaw("मैसेज में लिखा भी पढ़ो", "बंद रखना सुरक्षित है, आस-पास वाले भी सुन सकते हैं", Jarvis.announceBody) { Jarvis.updateFlag("announce_body", it) },
            lpm(MATCH, WRAP, top = 8)
        )
        col.addView(
            toggleRaw("फ़ोन आने और कटने की खबर", "किसका आया, बात हुई या छूट गया", Jarvis.announceCalls) { Jarvis.updateFlag("announce_call", it) },
            lpm(MATCH, WRAP, top = 8)
        )
        val nl = card()
        nl.addView(label(if (listenerOn()) "✓ नोटिफ़िकेशन की अनुमति मिली है (मैसेज की खबर चलेगी)" else "✗ मैसेज की खबर के लिए नोटिफ़िकेशन की अनुमति चाहिए", 14.5f, if (listenerOn()) C.GREEN else C.AMBER))
        if (!listenerOn()) {
            nl.addView(smallButton("अनुमति की सेटिंग खोलिए", C.CYAN) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }, lpm(MATCH, dp(44), top = 8))
        }
        col.addView(nl, lpm(MATCH, WRAP, top = 8))

        // ----- anumati -----
        col.addView(section("काम की अनुमति"), lpm(MATCH, WRAP, top = 18))
        col.addView(toggle("msg", "मैसेज", "स्क्रीन खुलती है, भेजना आप दबाते हैं"), lpm(MATCH, WRAP, top = 8))
        col.addView(toggle("call", "कॉल", "डायलर खुलता है, कॉल आप दबाते हैं"), lpm(MATCH, WRAP, top = 8))
        col.addView(toggle("phone", "फ़ोन कंट्रोल", "ऐप खोलना, स्क्रॉल, क्लिक, टॉर्च, आवाज़, अलार्म"), lpm(MATCH, WRAP, top = 8))

        val pay = hbox().apply {
            background = shape(C.SURFACE, C.RED, 14)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val pt = vbox()
        pt.addView(label("भुगतान", 15.5f, C.TEXT, true))
        pt.addView(label("JARVIS कभी पैसे नहीं देगा", 12.5f, C.MUTED))
        pay.addView(pt, lp(0, WRAP, 1f))
        pay.addView(label("हमेशा बंद", 13f, C.RED, true))
        col.addView(pay, lpm(MATCH, WRAP, top = 8))

        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val contactsOk = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val phoneOk = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val logOk = checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val a11yOk = JarvisAccessibilityService.instance != null
        val st = card()
        st.addView(label("फ़ोन की असली स्थिति", 16f, C.TEXT, true))
        st.addView(label((if (micOk) "✓ माइक" else "✗ माइक की अनुमति नहीं है"), 14.5f, if (micOk) C.GREEN else C.RED), lpm(MATCH, WRAP, top = 6))
        st.addView(label((if (contactsOk) "✓ कॉन्टैक्ट (नाम से कॉल/मैसेज, कॉलर का नाम)" else "✗ कॉन्टैक्ट की अनुमति नहीं (नंबर ही बोलना होगा)"), 14.5f, if (contactsOk) C.GREEN else C.AMBER), lpm(MATCH, WRAP, top = 4))
        st.addView(label((if (phoneOk && logOk) "✓ फ़ोन की खबर (कॉलर का नंबर)" else "✗ फ़ोन की खबर के लिए फ़ोन/कॉल-लॉग की अनुमति चाहिए"), 14.5f, if (phoneOk && logOk) C.GREEN else C.AMBER), lpm(MATCH, WRAP, top = 4))
        st.addView(label((if (a11yOk) "✓ फ़ोन कंट्रोल चालू" else "✗ फ़ोन कंट्रोल बंद (स्क्रॉल, क्लिक, पीछे, होम के लिए)"), 14.5f, if (a11yOk) C.GREEN else C.AMBER), lpm(MATCH, WRAP, top = 4))
        st.addView(label(if (Jarvis.online()) "✓ इंटरनेट चालू: AI सवाल-जवाब चल सकते हैं" else "✗ इंटरनेट बंद: सिर्फ़ फ़ोन के काम चलेंगे", 14.5f, if (Jarvis.online()) C.GREEN else C.AMBER), lpm(MATCH, WRAP, top = 4))
        if (!a11yOk) {
            st.addView(smallButton("फ़ोन कंट्रोल की सेटिंग खोलिए", C.CYAN) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }, lpm(MATCH, dp(44), top = 10))
        }
        col.addView(st, lpm(MATCH, WRAP, top = 12))

        val bg = card()
        bg.addView(label("ऐप बंद होने पर भी सुनता रहे", 16f, C.CYAN, true))
        bg.addView(label("ऐप को बंद (स्वाइप) करने पर भी सुनना चलता रहता है। पर बैटरी की रोक हटानी होगी, और फ़ोन रीस्टार्ट होने के बाद आने वाली नोटिफ़िकेशन दबानी होगी।", 13.5f, C.MUTED))
        bg.addView(smallButton("बैटरी की रोक हटाइए", C.CYAN) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, lpm(MATCH, dp(44), top = 8))
        col.addView(bg, lpm(MATCH, WRAP, top = 12))

        val honest = card()
        honest.addView(label("सच्ची रिपोर्ट", 16f, C.GREEN, true))
        honest.addView(label("जो काम हुआ, वही \"पूरा\" दिखेगा। जो नहीं हुआ, वह वजह के साथ दिखेगा।", 14.5f, C.MUTED))
        col.addView(honest, lpm(MATCH, WRAP, top = 12))

        col.addView(stopButton(), lpm(MATCH, dp(56), top = 20))
        return col
    }
}
