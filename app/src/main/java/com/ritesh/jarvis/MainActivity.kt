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
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_CONTACTS),
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
        drain()
        show(tab)
    }

    override fun onPause() {
        Jarvis.onEvent = null
        Jarvis.onState = null
        super.onPause()
    }

    private fun startVoice() {
        if (Jarvis.handsFree) VoiceService.start(this)
    }

    // ---------- jo kaam hua usko ofis mein dikhana ----------

    private fun drain() {
        val jobs = ArrayList(Jarvis.pending)
        Jarvis.pending.clear()
        for (j in jobs) {
            if (j.agent == "__stop") office.stopAll() else office.assign(j.agent, j.text, j.ok)
        }
        if (jobs.isNotEmpty() && tab != 2) show(tab)
    }

    private fun stateText(): String = when {
        !Jarvis.handsFree -> "माइक बंद · चालू करने के लिए गोले को दबाइए"
        Jarvis.speaking -> "बोल रहा हूँ…"
        Jarvis.listening -> "सुन रहा हूँ… बिना बटन दबाए बोलिए"
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
        return "● SYSTEM ONLINE  ·  MIC: $mic  ·  AGENTS: $live/${Agents.list.size}"
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
        val names = listOf("होम", "टीम", "ऑफिस", "काम", "सुरक्षा")
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
        col.addView(label("J.A.R.V.I.S", 26f, C.CYAN, true, true).apply { letterSpacing = 0.25f }, lpm(MATCH, WRAP, top = 6))

        val r = ReactorView(this)
        reactor = r
        r.mode = when {
            !Jarvis.handsFree -> 2
            Jarvis.speaking -> 1
            else -> 0
        }
        r.contentDescription = "माइक चालू या बंद करने के लिए दबाइए"
        r.setOnClickListener {
            Jarvis.setHandsFree(!Jarvis.handsFree)
            if (Jarvis.handsFree) startVoice()
            updateState()
        }
        col.addView(r, lpm(dp(250), dp(250), top = 10).apply { gravity = Gravity.CENTER_HORIZONTAL })

        val s2 = label(stateText(), 17f, C.TEXT, true).apply { gravity = Gravity.CENTER }
        status = s2
        col.addView(s2, lpm(MATCH, WRAP, top = 6))
        col.addView(label("\"जारविस, टॉर्च चालू करो\" या सीधे \"टॉर्च चालू करो\"", 13.5f, C.MUTED).apply { gravity = Gravity.CENTER }, lpm(MATCH, WRAP, top = 2))

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            val warn = card(C.AMBER)
            warn.addView(label("इस फ़ोन में आवाज़ पहचानने वाला Google ऐप नहीं मिला। Play Store से Google ऐप इंस्टॉल या अपडेट कीजिए, तब बिना बटन बोलना चलेगा। तब तक नीचे लिखकर बताइए।", 14f, C.AMBER))
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

        val row = hbox()
        val input = EditText(this).apply {
            hint = "या यहाँ लिखकर बताइए…"
            setHintTextColor(C.MUTED)
            setTextColor(C.TEXT)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            background = shape(C.SURFACE, C.BORDER, 24)
            setPadding(dp(18), 0, dp(18), 0)
        }
        val send = Button(this).apply {
            text = "भेजें"
            isAllCaps = false
            setTextColor(C.ON_CYAN)
            background = shape(C.CYAN, 0, 24)
            setOnClickListener { val t = input.text.toString(); input.setText(""); Jarvis.run(t) }
        }
        row.addView(input, lp(0, dp(48), 1f))
        row.addView(send, lpm(WRAP, dp(48), left = 10))
        col.addView(row, lpm(MATCH, WRAP, top = 16))

        val ex = card()
        ex.addView(label("ये बोलकर देखिए", 15f, C.CYAN, true, true))
        val samples = listOf(
            "रमेश को कॉल करो",
            "यूट्यूब पर अरिजीत का गाना चलाओ",
            "सुबह 6 बजे का अलार्म लगाओ",
            "टॉर्च चालू करो",
            "आवाज़ बढ़ाओ",
            "दिल्ली का रास्ता दिखाओ",
            "नोट लिखो दूध लाना है",
            "25 गुणा 4 कितना हुआ",
            "नीचे स्क्रॉल करो",
            "क्या कर सकते हो"
        )
        for (s in samples) ex.addView(label("› $s", 14f, C.MUTED), lpm(MATCH, WRAP, top = 6))
        col.addView(ex, lpm(MATCH, WRAP, top = 16))
        return col
    }

    // ---------- 2. team ----------

    private fun team(): View {
        val col = page()
        val live = Agents.list.count { it.live }
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
            r.addView(dot(if (a.live) C.GREEN else C.MUTED), lpm(dp(8), dp(8), left = 6))
            r.addView(label(if (a.live) "तैयार" else "जल्द आएगा", 13f, if (a.live) C.GREEN else C.MUTED), lpm(WRAP, WRAP, left = 6))
            col.addView(r, lpm(MATCH, WRAP, top = 8))
        }
        return col
    }

    // ---------- ofis ----------

    private fun officeTab(): View {
        val col = page()
        col.addView(title("JARVIS ऑफिस", "Manager बुलाता है, एजेंट डायरी लेकर आता है, फिर अपनी सीट पर काम करता है"))
        col.addView(office, lpm(MATCH, dp(700), top = 14))
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

    // ---------- 4. suraksha ----------

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

    private fun safety(): View {
        val col = page()
        col.addView(title("अनुमति और सुरक्षा", "JARVIS क्या कर सकता है, यह आप तय करते हैं"))

        col.addView(
            toggleRaw("हमेशा सुनो (बिना बटन)", "माइक चालू रहता है, बोलते ही काम करता है", Jarvis.handsFree) {
                Jarvis.setHandsFree(it)
                if (it) startVoice()
            }, lpm(MATCH, WRAP, top = 14)
        )
        col.addView(
            toggleRaw("सिर्फ़ \"जारविस\" कहने पर काम करो", "आस-पास की बातचीत से गलती से काम नहीं होगा", Jarvis.wakeOnly) {
                Jarvis.setWakeOnly(it)
            }, lpm(MATCH, WRAP, top = 8)
        )
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
        val a11yOk = JarvisAccessibilityService.instance != null
        val st = card()
        st.addView(label("फ़ोन की असली स्थिति", 16f, C.TEXT, true))
        st.addView(label((if (micOk) "✓ माइक की अनुमति मिली है" else "✗ माइक की अनुमति नहीं है"), 14.5f, if (micOk) C.GREEN else C.RED), lpm(MATCH, WRAP, top = 6))
        st.addView(label((if (contactsOk) "✓ कॉन्टैक्ट पढ़ सकता है (नाम से कॉल/मैसेज)" else "✗ कॉन्टैक्ट की अनुमति नहीं (नाम से कॉल नहीं होगा, नंबर बोलना होगा)"), 14.5f, if (contactsOk) C.GREEN else C.AMBER), lpm(MATCH, WRAP, top = 4))
        st.addView(label((if (a11yOk) "✓ फ़ोन कंट्रोल चालू है" else "✗ फ़ोन कंट्रोल बंद है (स्क्रॉल, क्लिक, पीछे, होम के लिए चालू करें)"), 14.5f, if (a11yOk) C.GREEN else C.AMBER), lpm(MATCH, WRAP, top = 4))
        if (!a11yOk) {
            st.addView(Button(this).apply {
                text = "सेटिंग खोलिए"
                isAllCaps = false
                setTextColor(C.ON_CYAN)
                background = shape(C.CYAN, 0, 12)
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }, lpm(MATCH, dp(44), top = 10))
        }
        col.addView(st, lpm(MATCH, WRAP, top = 12))

        val bg = card()
        bg.addView(label("बैकग्राउंड में सुनता रहे", 16f, C.CYAN, true))
        bg.addView(label("फ़ोन की सेटिंग > ऐप > JARVIS AI > बैटरी में \"Unrestricted / बिना रोक\" चुनिए, वरना कुछ फ़ोन ऐप को बंद कर देते हैं।", 14f, C.MUTED))
        col.addView(bg, lpm(MATCH, WRAP, top = 12))

        val honest = card()
        honest.addView(label("सच्ची रिपोर्ट", 16f, C.GREEN, true))
        honest.addView(label("जो काम हुआ, वही \"पूरा\" दिखेगा। जो नहीं हुआ, वह वजह के साथ दिखेगा।", 14.5f, C.MUTED))
        col.addView(honest, lpm(MATCH, WRAP, top = 12))

        col.addView(stopButton(), lpm(MATCH, dp(56), top = 20))
        return col
    }
}
