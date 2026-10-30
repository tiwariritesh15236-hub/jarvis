package com.ritesh.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var router: CommandRouter
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var office: OfficeView

    private var tab = 0
    private var heard = ""
    private var reply: CommandRouter.Reply? = null
    private var orb: OrbView? = null
    private var status: TextView? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.NAV
        router = CommandRouter(this)
        office = OfficeView(this)
        tts = TextToSpeech(this, this)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)

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
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) show(tab)
    }

    override fun onInit(status: Int) {
        tts.language = Locale("hi", "IN")
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    // ---------- kaam chalana ----------

    private fun run(cmd: String) {
        if (cmd.isBlank()) return
        heard = cmd
        val r = router.handle(cmd)
        reply = r
        TaskLog.add(r.agent, "$cmd → ${r.text}", if (r.ok) TaskLog.DONE else TaskLog.FAILED)
        office.assign(r.agent, cmd, r.ok)
        tts.speak(r.text, TextToSpeech.QUEUE_FLUSH, null, "j")
        show(tab)
    }

    private fun stopAll() {
        tts.stop()
        office.stopAll()
        TaskLog.add("manager", "आपने इमरजेंसी स्टॉप दबाया। सब रुका।", TaskLog.STOPPED)
        show(tab)
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलिए…")
        try {
            orb?.listening = true
            status?.text = "सुन रहा हूँ… बोलिए"
            startActivityForResult(i, 7)
        } catch (e: Exception) {
            orb?.listening = false
            val r = CommandRouter.Reply("manager", "इस फ़ोन में आवाज़ पहचानने वाला Google ऐप नहीं मिला। नीचे लिखकर बताइए।", false)
            reply = r
            TaskLog.add("manager", r.text, TaskLog.FAILED)
            show(tab)
        }
    }

    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        orb?.listening = false
        if (rq == 7 && rs == RESULT_OK) {
            val said = d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (said != null) run(said) else show(tab)
        } else {
            status?.text = "कुछ सुनाई नहीं दिया। फिर से दबाइए।"
        }
    }

    // ---------- tab ----------

    private fun show(i: Int) {
        tab = i
        content.removeAllViews()
        (office.parent as? android.view.ViewGroup)?.removeView(office)
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
                textSize = 14f
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
        setOnClickListener { stopAll() }
    }

    // ---------- 1. home ----------

    private fun home(): View {
        val col = page()
        col.addView(label("JARVIS", 15f, C.CYAN, true).apply { letterSpacing = 0.22f })

        val o = OrbView(this)
        orb = o
        o.contentDescription = "बोलने के लिए दबाइए"
        o.setOnClickListener { listen() }
        col.addView(o, lpm(dp(220), dp(220), top = 12).apply { gravity = Gravity.CENTER_HORIZONTAL })

        val st = label("माइक दबाइए और बोलिए", 20f, C.TEXT, true).apply { gravity = Gravity.CENTER }
        status = st
        col.addView(st, lpm(MATCH, WRAP, top = 4))
        col.addView(label("हिंदी, Hinglish या English में बोल सकते हैं", 14f, C.MUTED).apply { gravity = Gravity.CENTER })

        if (heard.isNotEmpty()) {
            val c1 = card()
            c1.addView(label("आपने कहा", 12.5f, C.MUTED))
            c1.addView(label("\"$heard\"", 16f, C.TEXT))
            col.addView(c1, lpm(MATCH, WRAP, top = 18))
        }
        reply?.let { r ->
            val c2 = card(if (r.ok) C.AMBER else C.RED)
            c2.addView(label(Agents.nameOf(r.agent), 12.5f, if (r.ok) C.AMBER else C.RED, true))
            c2.addView(label(r.text, 16f, C.TEXT))
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
            setOnClickListener { val t = input.text.toString(); input.setText(""); run(t) }
        }
        row.addView(input, lp(0, dp(48), 1f))
        row.addView(send, lpm(WRAP, dp(48), left = 10))
        col.addView(row, lpm(MATCH, WRAP, top = 20))
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
        col.addView(title("JARVIS ऑफिस", "आप हुक्म दीजिए, Manager एजेंट को बुलाकर काम देता है"))
        col.addView(office, lpm(MATCH, dp(520), top = 14))
        col.addView(label("यहाँ जो चलता है वह असली काम से चलता है। होम पर जाकर बोलिए, फिर यहाँ देखिए।", 13.5f, C.MUTED), lpm(MATCH, WRAP, top = 12))
        return col
    }

    // ---------- 3. kaam ----------

    private fun tasks(): View {
        val col = page()
        col.addView(title("लाइव काम", "जो सच में हुआ, वही यहाँ दिखता है"))

        if (TaskLog.entries.isEmpty()) {
            val c = card()
            c.addView(label("अभी कोई काम नहीं हुआ। होम पर जाकर बोलिए।", 15f, C.MUTED))
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

    private fun toggle(key: String, name: String, sub: String): View {
        val r = hbox().apply {
            background = shape(C.SURFACE, C.BORDER, 14)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val t = vbox()
        t.addView(label(name, 15.5f, C.TEXT, true))
        t.addView(label(sub, 12.5f, C.MUTED))
        val sw = Switch(this).apply {
            isChecked = router.allowed(key)
            thumbTintList = ColorStateList.valueOf(C.TEXT)
            trackTintList = ColorStateList.valueOf(if (isChecked) C.CYAN else C.BORDER)
            contentDescription = name
            setOnCheckedChangeListener { b, v ->
                router.setAllowed(key, v)
                (b as Switch).trackTintList = ColorStateList.valueOf(if (v) C.CYAN else C.BORDER)
            }
        }
        r.addView(t, lp(0, WRAP, 1f))
        r.addView(sw)
        return r
    }

    private fun safety(): View {
        val col = page()
        col.addView(title("अनुमति और सुरक्षा", "JARVIS क्या कर सकता है, यह आप तय करते हैं"))

        col.addView(toggle("msg", "मैसेज", "स्क्रीन खुलती है, भेजना आप दबाते हैं"), lpm(MATCH, WRAP, top = 14))
        col.addView(toggle("call", "कॉल", "डायलर खुलता है, कॉल आप दबाते हैं"), lpm(MATCH, WRAP, top = 8))
        col.addView(toggle("phone", "फ़ोन कंट्रोल", "ऐप खोलना, पीछे, होम, टाइमर"), lpm(MATCH, WRAP, top = 8))

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

        // asli haal
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val a11yOk = JarvisAccessibilityService.instance != null
        val st = card()
        st.addView(label("फ़ोन की असली स्थिति", 16f, C.TEXT, true))
        st.addView(label((if (micOk) "✓ माइक की अनुमति मिली है" else "✗ माइक की अनुमति नहीं है"), 14.5f, if (micOk) C.GREEN else C.RED), lpm(MATCH, WRAP, top = 6))
        st.addView(label((if (a11yOk) "✓ फ़ोन कंट्रोल चालू है" else "✗ फ़ोन कंट्रोल बंद है (पीछे, होम, नोटिफ़िकेशन के लिए चालू करें)"), 14.5f, if (a11yOk) C.GREEN else C.AMBER), lpm(MATCH, WRAP, top = 4))
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

        val honest = card()
        honest.addView(label("सच्ची रिपोर्ट", 16f, C.GREEN, true))
        honest.addView(label("जो काम हुआ, वही \"पूरा\" दिखेगा। जो नहीं हुआ, वह वजह के साथ दिखेगा।", 14.5f, C.MUTED))
        col.addView(honest, lpm(MATCH, WRAP, top = 12))

        col.addView(stopButton(), lpm(MATCH, dp(56), top = 20))
        return col
    }
}
