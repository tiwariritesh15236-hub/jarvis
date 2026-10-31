package com.ritesh.jarvis

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JARVIS ka Manager. Hindi/Hinglish hukm ko samajhkar sahi agent ko deta hai
 * aur asli Android action karta hai. Jo nahi ho sakta, wo saaf bolta hai.
 */
class CommandRouter(private val ctx: Context) {

    data class Reply(val agent: String, val text: String, val ok: Boolean = true, val unknown: Boolean = false)

    private val prefs = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun allowed(key: String): Boolean = prefs.getBoolean("allow_$key", true)
    fun setAllowed(key: String, v: Boolean) { prefs.edit().putBoolean("allow_$key", v).apply() }

    private class App(val keys: List<String>, val pkg: String, val label: String)

    private val apps = listOf(
        App(listOf("youtube", "यूट्यूब"), "com.google.android.youtube", "YouTube"),
        App(listOf("whatsapp", "व्हाट्सएप", "व्हाट्सऐप", "वॉट्सएप", "वाट्सएप"), "com.whatsapp", "WhatsApp"),
        App(listOf("gmail", "जीमेल"), "com.google.android.gm", "Gmail"),
        App(listOf("maps", "मैप्स", "मैप"), "com.google.android.apps.maps", "Maps"),
        App(listOf("chrome", "क्रोम"), "com.android.chrome", "Chrome"),
        App(listOf("instagram", "इंस्टाग्राम"), "com.instagram.android", "Instagram"),
        App(listOf("facebook", "फेसबुक"), "com.facebook.katana", "Facebook"),
        App(listOf("telegram", "टेलीग्राम"), "org.telegram.messenger", "Telegram"),
        App(listOf("spotify", "स्पॉटिफाई"), "com.spotify.music", "Spotify"),
        App(listOf("camera", "कैमरा"), "", "कैमरा")
    )

    private fun start(i: Intent): Boolean {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); true } catch (e: Exception) { false }
    }

    private fun has(q: String, re: String) = Regex(re).containsMatchIn(q)
    private fun tokens(q: String) = q.split(Regex("\\s+")).filter { it.isNotBlank() }
    private fun strip(q: String, stop: Set<String>) = tokens(q).filter { it !in stop }.joinToString(" ")

    private fun blocked(agent: String, what: String) =
        Reply(agent, "$what की अनुमति बंद है। सुरक्षा टैब में चालू कीजिए।", false)

    private fun memories(): List<String> =
        (prefs.getString("mem", "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun notes(): List<String> =
        (prefs.getString("notes", "") ?: "").split("\n").filter { it.isNotBlank() }

    private val callStop = setOf(
        "call", "phone", "फोन", "फ़ोन", "कॉल", "laga", "लगा", "lagao", "लगाओ", "karo", "करो", "kijiye", "कीजिए",
        "do", "दो", "mujhe", "मुझे", "ko", "को", "sms", "message", "मैसेज", "संदेश", "bhejo", "भेजो", "bhej", "भेज",
        "send", "text", "kar", "कर", "ek", "एक"
    )

    private fun findNumber(name: String): String? {
        if (name.isBlank()) return null
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$name%"), null
        ) ?: return null
        return c.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private var torchOn = false

    fun handle(raw: String): Reply {
        val q = raw.lowercase(Locale.getDefault()).trim()
        val a11y = JarvisAccessibilityService.instance
        val number = Regex("\\+?\\d[\\d ]{6,}").find(q)?.value?.replace(" ", "")

        return when {
            // ---- paisa: kabhi nahi ----
            has(q, "payment|भुगतान|upi|पैसे भेज|paise bhej|ट्रांसफर|transfer") ->
                Reply("manager", "भुगतान का काम मैं कभी नहीं करता। यह आपकी ही शर्त है।", false)

            // ---- manager khud ----
            has(q, "kaun ho|tum kaun|aap kaun|who are you|तुम कौन|आप कौन|तुम्हारा नाम|tumhara naam|तुम्हारा नाम") ->
                Reply("manager", "मैं JARVIS हूँ, आपका अपना असिस्टेंट। मेरी टीम में कई एजेंट हैं, और मैं आपके फ़ोन पर बोलकर काम करता हूँ।")
            has(q, "namaste|नमस्ते|hello|हैलो|हेलो|सुप्रभात|good morning") ->
                Reply("manager", "नमस्ते! बताइए, क्या काम है?")
            has(q, "thank|धन्यवाद|शुक्रिया|shukriya|dhanyavad") ->
                Reply("manager", "आपका स्वागत है।")
            has(q, "kya kar sakte|क्या कर सकते|क्या कर सकता|help|मदद|कौन कौन से काम") ->
                Reply("manager", "मैं ऐप खोल सकता हूँ, कॉल और मैसेज तैयार कर सकता हूँ, अलार्म, टाइमर, टॉर्च, आवाज़, खोज, रास्ता, गाना, नोट, गणित, स्क्रॉल, स्क्रीनशॉट और याद रखना कर सकता हूँ।")

            // ---- memory ----
            has(q, "yaad rakh|याद रख|remember") -> {
                val what = strip(q, setOf("yaad", "rakho", "rakh", "याद", "रखो", "रख", "remember", "ye", "यह", "ki", "कि", "mera", "मेरा"))
                if (what.isEmpty()) Reply("memory", "क्या याद रखूँ? बोलिए, जैसे: याद रखो कल मीटिंग है।", false)
                else {
                    prefs.edit().putString("mem", (memories() + what).takeLast(50).joinToString("\n")).apply()
                    Reply("memory", "याद रख लिया: $what")
                }
            }
            has(q, "kya yaad|yaad hai|क्या याद|याद है") -> {
                val m = memories()
                if (m.isEmpty()) Reply("memory", "अभी मुझे कुछ याद रखने को नहीं कहा गया।")
                else Reply("memory", "मुझे याद है: " + m.takeLast(5).joinToString("। "))
            }
            has(q, "bhul ja|bhool ja|भूल जा|भूल जाओ|forget") -> {
                prefs.edit().remove("mem").apply()
                Reply("memory", "सब भूल गया।")
            }

            // ---- notes ----
            has(q, "note|नोट") && !has(q, "notif|नोटिफ") -> {
                when {
                    has(q, "padho|पढ़|पढ|sunao|सुनाओ|batao|बताओ|dikhao|दिखाओ|kya|क्या") -> {
                        val n = notes()
                        if (n.isEmpty()) Reply("notes", "अभी कोई नोट नहीं है।")
                        else Reply("notes", "आपके नोट: " + n.takeLast(5).joinToString("। "))
                    }
                    has(q, "mita|मिटा|delete|saaf|साफ|clear|हटा") -> {
                        prefs.edit().remove("notes").apply()
                        Reply("notes", "सब नोट मिटा दिए।")
                    }
                    else -> {
                        val t = strip(q, setOf("note", "notes", "नोट", "नोट्स", "likho", "लिखो", "likh", "लिख", "karo", "करो", "lo", "लो", "kar", "कर", "ye", "यह", "ki", "कि", "mera", "मेरा"))
                        if (t.isEmpty()) Reply("notes", "क्या लिखूँ? बोलिए, जैसे: नोट लिखो दूध लाना है।", false)
                        else {
                            prefs.edit().putString("notes", (notes() + t).takeLast(100).joinToString("\n")).apply()
                            Reply("notes", "नोट लिख लिया: $t")
                        }
                    }
                }
            }

            // ---- device ----
            has(q, "battery|बैटरी") -> {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                Reply("device", "बैटरी ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} प्रतिशत है।")
            }
            has(q, "torch|टॉर्च|टोर्च|flashlight|फ्लैशलाइट|फ़्लैश|फ्लैश|flash") -> {
                if (!allowed("phone")) return blocked("device", "फ़ोन कंट्रोल")
                try {
                    val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    if (id == null) Reply("device", "इस फ़ोन में टॉर्च नहीं मिली।", false)
                    else {
                        val t = tokens(q).toSet()
                        val on = when {
                            t.any { it in setOf("off", "band", "बंद", "bujhao", "बुझाओ", "bujha", "बुझा") } -> false
                            t.any { it in setOf("on", "chalu", "चालू", "jalao", "जलाओ", "jala", "जला", "kholo", "खोलो") } -> true
                            else -> !torchOn
                        }
                        cm.setTorchMode(id, on)
                        torchOn = on
                        Reply("device", if (on) "टॉर्च चालू कर दी।" else "टॉर्च बंद कर दी।")
                    }
                } catch (e: Exception) {
                    Reply("device", "टॉर्च नहीं चल पाई।", false)
                }
            }
            has(q, "volume|वॉल्यूम|वाल्यूम|awaaz|aawaz|आवाज") -> {
                if (!allowed("phone")) return blocked("device", "फ़ोन कंट्रोल")
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                when {
                    has(q, "mute|म्यूट|silent|साइलेंट") -> {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                        Reply("device", "आवाज़ बंद कर दी।")
                    }
                    has(q, "badha|बढ|zyada|ज़्यादा|ज्यादा|ज़्यादा|tez|तेज|\\bup\\b|loud|ऊँची|ऊंची") -> {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        Reply("device", "आवाज़ बढ़ा दी।")
                    }
                    has(q, "kam|कम|ghata|घटा|dheere|धीमा|धीरे|\\bdown\\b|\\blow\\b") -> {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        Reply("device", "आवाज़ कम कर दी।")
                    }
                    else -> Reply("device", "आवाज़ बढ़ानी है या कम करनी है?", false)
                }
            }
            has(q, "wifi|wi-fi|वाईफाई|वाई-फाई|वाई फाई|वाईफ़ाई") -> {
                if (!allowed("phone")) return blocked("device", "फ़ोन कंट्रोल")
                if (start(Intent(Settings.ACTION_WIFI_SETTINGS))) Reply("device", "वाई-फाई की सेटिंग खोल दी।")
                else Reply("device", "सेटिंग नहीं खुल पाई।", false)
            }
            has(q, "bluetooth|ब्लूटूथ") -> {
                if (!allowed("phone")) return blocked("device", "फ़ोन कंट्रोल")
                if (start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) Reply("device", "ब्लूटूथ की सेटिंग खोल दी।")
                else Reply("device", "सेटिंग नहीं खुल पाई।", false)
            }
            has(q, "settings|सेटिंग") -> {
                if (!allowed("phone")) return blocked("device", "फ़ोन कंट्रोल")
                if (start(Intent(Settings.ACTION_SETTINGS))) Reply("device", "सेटिंग खोल दी।")
                else Reply("device", "सेटिंग नहीं खुल पाई।", false)
            }

            // ---- time ----
            has(q, "alarm|अलार्म|jagao|जगाओ|jagana|जगाना") -> {
                if (!allowed("phone")) return blocked("time", "फ़ोन कंट्रोल")
                val m = Regex("(\\d{1,2})(?::(\\d{2}))?").find(q)
                if (m == null) Reply("time", "कितने बजे का अलार्म? बोलिए, जैसे: सुबह 6 बजे का अलार्म।", false)
                else {
                    var hr = m.groupValues[1].toInt()
                    val mn = m.groupValues[2].toIntOrNull() ?: 0
                    if (has(q, "pm|शाम|sham|raat|रात|dopahar|दोपहर") && hr < 12) hr += 12
                    if (hr > 23 || mn > 59) Reply("time", "यह समय सही नहीं लगा।", false)
                    else {
                        val ok = start(Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, hr)
                            .putExtra(AlarmClock.EXTRA_MINUTES, mn)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                        if (ok) Reply("time", "$hr बजकर $mn मिनट का अलार्म लग गया।")
                        else Reply("time", "अलार्म नहीं लग पाया।", false)
                    }
                }
            }
            has(q, "timer|टाइमर") -> {
                if (!allowed("phone")) return blocked("time", "फ़ोन कंट्रोल")
                val m = Regex("(\\d+)").find(q)?.value?.toIntOrNull() ?: 5
                val ok = start(Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, m * 60)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                if (ok) Reply("time", "$m मिनट का टाइमर लग गया।") else Reply("time", "टाइमर नहीं लग पाया।", false)
            }
            has(q, "tareekh|तारीख|date kya|आज कौन सा दिन|aaj kaun") ->
                Reply("time", "आज " + SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("hi", "IN")).format(Date()) + " है।")
            has(q, "samay|kitne baje|कितने बजे|समय|टाइम क्या|time kya|what time") ->
                Reply("time", "अभी " + SimpleDateFormat("hh:mm a", Locale.US).format(Date()) + " बजे हैं।")

            // ---- music / nav / search ----
            has(q, "play|chalao|चलाओ|gana|गाना|song|bajao|बजाओ") -> {
                val s = strip(q, setOf(
                    "play", "chalao", "चलाओ", "gana", "गाना", "song", "bajao", "बजाओ", "youtube", "यूट्यूब", "pe", "par", "पर", "पे",
                    "on", "karo", "करो", "do", "दो", "mera", "मेरा", "ek", "एक", "ka", "का", "ki", "की", "mujhe", "मुझे", "sunao", "सुनाओ"))
                if (s.isEmpty()) Reply("music", "कौन सा गाना चलाऊँ?", false)
                else if (start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(s)))))
                    Reply("music", "YouTube पर \"$s\" खोल दिया।")
                else Reply("music", "YouTube नहीं खुल पाया।", false)
            }
            has(q, "navigate|रास्ता|rasta|direction|route|le chalo|ले चलो|नेविगेट") -> {
                val s = strip(q, setOf(
                    "navigate", "नेविगेट", "रास्ता", "rasta", "direction", "directions", "route", "ले", "le", "चलो", "chalo", "mujhe", "मुझे",
                    "ka", "का", "ke", "के", "liye", "लिए", "tak", "तक", "to", "ko", "को", "batao", "बताओ", "dikhao", "दिखाओ", "karo", "करो", "jao", "जाओ"))
                if (s.isEmpty()) Reply("nav", "कहाँ का रास्ता चाहिए?", false)
                else if (start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(s)))))
                    Reply("nav", "\"$s\" का रास्ता Maps में खोल दिया।")
                else Reply("nav", "Maps नहीं खुल पाया।", false)
            }
            (has(q, "search|सर्च|khojo|खोजो|dhundo|ढूंढो|ढूँढो|dhundh|ढूंढ") ||
                (has(q, "google|गूगल") && !has(q, "kholo|khol|खोलो|खोल|open|ओपन"))) -> {
                val s = strip(q, setOf(
                    "search", "सर्च", "khojo", "खोजो", "google", "गूगल", "dhundo", "ढूंढो", "ढूँढो", "karo", "करो", "mere", "liye", "लिए",
                    "about", "ke", "के", "baare", "बारे", "mein", "में", "par", "पर", "internet", "इंटरनेट", "pe", "पे", "ko", "को"))
                if (s.isEmpty()) Reply("search", "क्या खोजूँ?", false)
                else if (start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(s)))))
                    Reply("search", "\"$s\" Google पर खोल दिया।")
                else Reply("search", "खोज नहीं खुल पाई।", false)
            }

            // ---- screen (accessibility) ----
            has(q, "screenshot|स्क्रीनशॉट|स्क्रीन शॉट") -> {
                if (!allowed("phone")) return blocked("screen", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y("screen")
                else if (a11y.screenshot()) Reply("screen", "स्क्रीनशॉट ले लिया।")
                else Reply("screen", "इस फ़ोन में स्क्रीनशॉट नहीं हो पाया।", false)
            }
            has(q, "lock|लॉक|screen band|स्क्रीन बंद") -> {
                if (!allowed("phone")) return blocked("screen", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y("screen")
                else if (a11y.lockScreen()) Reply("screen", "फ़ोन लॉक कर दिया।")
                else Reply("screen", "फ़ोन लॉक नहीं हो पाया।", false)
            }
            has(q, "scroll|स्क्रॉल|neeche|नीचे|upar|ऊपर") -> {
                if (!allowed("phone")) return blocked("screen", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y("screen")
                else {
                    val down = !has(q, "upar|ऊपर|\\bup\\b")
                    a11y.scroll(down)
                    Reply("screen", if (down) "नीचे स्क्रॉल किया।" else "ऊपर स्क्रॉल किया।")
                }
            }
            has(q, "click|tap|dabao|दबाओ|क्लिक|टैप") -> {
                if (!allowed("phone")) return blocked("screen", "फ़ोन कंट्रोल")
                val s = strip(q, setOf("click", "tap", "dabao", "दबाओ", "क्लिक", "टैप", "karo", "करो", "par", "पर", "pe", "पे", "on", "button", "बटन", "ko", "को"))
                if (a11y == null) needA11y("screen")
                else if (s.isEmpty()) Reply("screen", "किस पर क्लिक करूँ? स्क्रीन पर जो लिखा है वह बोलिए।", false)
                else if (a11y.clickText(s)) Reply("screen", "\"$s\" पर क्लिक कर दिया।")
                else Reply("screen", "स्क्रीन पर \"$s\" नहीं मिला।", false)
            }
            has(q, "recent|रीसेंट") -> {
                if (!allowed("phone")) return blocked("screen", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y("screen") else { a11y.openRecents(); Reply("screen", "हाल के ऐप खोल दिए।") }
            }
            has(q, "back|peeche|वापस|पीछे") -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y("phone") else { a11y.goBack(); Reply("phone", "पीछे गया।") }
            }
            has(q, "notification|नोटिफ") -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y("phone") else { a11y.openNotifications(); Reply("phone", "नोटिफ़िकेशन खोल दिए।") }
            }
            has(q, "\\bhome\\b|ghar|होम") -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y("phone") else { a11y.goHome(); Reply("phone", "होम पर आ गया।") }
            }

            // ---- open app ----
            has(q, "kholo|khol|खोलो|खोल|open|ओपन") -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                val app = apps.firstOrNull { a -> a.keys.any { q.contains(it) } }
                when {
                    app == null -> Reply("phone", "कौन सा ऐप खोलूँ? अभी ये हैं: ${apps.joinToString { it.label }}।", false)
                    app.pkg.isEmpty() ->
                        if (start(Intent(MediaStore.ACTION_IMAGE_CAPTURE))) Reply("phone", "कैमरा खोल दिया।") else Reply("phone", "कैमरा नहीं खुल पाया।", false)
                    else -> {
                        val i = ctx.packageManager.getLaunchIntentForPackage(app.pkg)
                        if (i == null) Reply("phone", "${app.label} इस फ़ोन में नहीं है।", false)
                        else if (start(i)) Reply("phone", "${app.label} खोल दिया।")
                        else Reply("phone", "${app.label} नहीं खुल पाया।", false)
                    }
                }
            }

            // ---- call / message ----
            has(q, "call|phone laga|फोन लगा|फ़ोन लगा|कॉल") -> {
                if (!allowed("call")) return blocked("call", "कॉल")
                val name = strip(q, callStop).replace(Regex("[\\d+]"), "").trim()
                val n = number ?: findNumber(name)
                when {
                    n != null && start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) ->
                        Reply("call", "डायलर खोल दिया" + (if (name.isNotEmpty() && number == null) " ($name)" else "") + "। कॉल का बटन आप दबाइए।")
                    name.isNotEmpty() && number == null ->
                        Reply("call", "\"$name\" नाम का नंबर नहीं मिला। कॉन्टैक्ट की अनुमति दी है? या नंबर बोलिए।", false)
                    else -> Reply("call", "किसे कॉल करूँ? नाम या नंबर बोलिए।", false)
                }
            }
            has(q, "sms|message|मैसेज|संदेश") -> {
                if (!allowed("msg")) return blocked("msg", "मैसेज")
                val parts = q.split(Regex("\\s(ki|कि|that)\\s"), 2)
                val head = parts[0]
                val body = if (parts.size > 1) parts[1].trim() else ""
                val name = strip(head, callStop).replace(Regex("[\\d+]"), "").trim()
                val n = number ?: findNumber(name) ?: ""
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$n")).putExtra("sms_body", body)
                if (start(i)) Reply("msg", "मैसेज की स्क्रीन खोल दी" + (if (body.isNotEmpty()) ", लिख भी दिया" else "") + "। भेजना आप दबाइए।")
                else Reply("msg", "मैसेज नहीं खुल पाया।", false)
            }

            // ---- calc ----
            else -> {
                val c = calc(q)
                if (c != null) Reply("calc", c)
                else Reply("manager", "यह काम अभी मुझे नहीं आता। \"क्या कर सकते हो\" बोलकर मेरे काम सुन लीजिए।", false, true)
            }
        }
    }

    private fun needA11y(agent: String): Reply {
        start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return Reply(agent, "पहले सेटिंग में JARVIS को चालू कीजिए। सेटिंग खोल दी है।", false)
    }

    // ---------- ganit ----------

    private class MathParser(val s: String) {
        var p = 0
        fun parse(): Double { val v = expr(); if (p != s.length) throw IllegalArgumentException(); return v }
        private fun expr(): Double {
            var v = term()
            while (p < s.length && (s[p] == '+' || s[p] == '-')) { val op = s[p++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        private fun term(): Double {
            var v = factor()
            while (p < s.length && (s[p] == '*' || s[p] == '/')) { val op = s[p++]; val r = factor(); v = if (op == '*') v * r else v / r }
            return v
        }
        private fun factor(): Double {
            if (p < s.length && s[p] == '-') { p++; return -factor() }
            val st = p
            while (p < s.length && (s[p].isDigit() || s[p] == '.')) p++
            if (st == p) throw IllegalArgumentException()
            return s.substring(st, p).toDouble()
        }
    }

    private fun calc(q: String): String? {
        var n = q
            .replace(Regex("गुणा|multiply|times|into"), "*")
            .replace(Regex("भाग|divided by|divide"), "/")
            .replace(Regex("घटा|माइनस|minus"), "-")
            .replace(Regex("जोड़|जोड़ो|प्लस|plus"), "+")
            .replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*")
        val m = Regex("\\d+(?:\\.\\d+)?(?:\\s*[+\\-*/]\\s*\\d+(?:\\.\\d+)?)+").find(n) ?: return null
        n = m.value.replace(" ", "")
        return try {
            val v = MathParser(n).parse()
            if (v.isInfinite() || v.isNaN()) "शून्य से भाग नहीं हो सकता।"
            else "$n का जवाब " + (if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else String.format(Locale.US, "%.2f", v)) + " है।"
        } catch (e: Exception) {
            null
        }
    }
}
