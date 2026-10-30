package com.ritesh.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
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

    data class Reply(val agent: String, val text: String, val ok: Boolean = true)

    private val prefs = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun allowed(key: String): Boolean = prefs.getBoolean("allow_$key", true)
    fun setAllowed(key: String, v: Boolean) { prefs.edit().putBoolean("allow_$key", v).apply() }

    private val apps = mapOf(
        "youtube" to "com.google.android.youtube", "whatsapp" to "com.whatsapp",
        "gmail" to "com.google.android.gm", "maps" to "com.google.android.apps.maps",
        "chrome" to "com.android.chrome", "camera" to ""
    )

    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    private fun blocked(agent: String, what: String) =
        Reply(agent, "$what की अनुमति बंद है। सुरक्षा टैब में चालू कीजिए।", false)

    private fun memories(): List<String> =
        (prefs.getString("mem", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun handle(raw: String): Reply {
        val q = raw.lowercase(Locale.getDefault()).trim()
        val a11y = JarvisAccessibilityService.instance
        val number = Regex("\\+?\\d[\\d ]{6,}").find(q)?.value?.replace(" ", "")

        return when {
            Regex("kaun ho|tum kaun|aap kaun|who are you|तुम कौन|आप कौन|तुम्हारा नाम|tumhara naam").containsMatchIn(q) ->
                Reply("manager", "मैं JARVIS हूँ, आपका अपना असिस्टेंट। मैं आपके फ़ोन पर बोलकर काम कर सकता हूँ।")

            Regex("yaad rakh|याद रख|remember").containsMatchIn(q) -> {
                val what = q.replace(Regex("yaad rakho|yaad rakh|याद रखो|याद रख|remember"), "").trim()
                if (what.isEmpty()) Reply("memory", "क्या याद रखूँ? बोलिए, जैसे: याद रखो कल मीटिंग है।", false)
                else {
                    prefs.edit().putString("mem", (memories() + what).takeLast(50).joinToString("\n")).apply()
                    Reply("memory", "याद रख लिया: $what")
                }
            }
            Regex("kya yaad|yaad hai|क्या याद|याद है").containsMatchIn(q) -> {
                val m = memories()
                if (m.isEmpty()) Reply("memory", "अभी मुझे कुछ याद रखने को नहीं कहा गया।")
                else Reply("memory", "मुझे याद है: " + m.takeLast(5).joinToString("। "))
            }
            Regex("bhul ja|bhool ja|भूल जा|sab bhul|forget").containsMatchIn(q) -> {
                prefs.edit().remove("mem").apply()
                Reply("memory", "सब भूल गया।")
            }

            Regex("battery|बैटरी").containsMatchIn(q) -> {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                Reply("phone", "बैटरी ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} प्रतिशत है।")
            }
            Regex("samay|time kya|kitne baje|समय|कितने बजे|टाइम").containsMatchIn(q) ->
                Reply("manager", "अभी " + SimpleDateFormat("hh:mm a", Locale.US).format(Date()) + " बजे हैं।")

            Regex("kholo|khol |open|खोल").containsMatchIn(q) -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                val name = apps.keys.firstOrNull { q.contains(it) }
                when {
                    name == "camera" -> { start(Intent(MediaStore.ACTION_IMAGE_CAPTURE)); Reply("phone", "कैमरा खोल दिया।") }
                    name != null -> {
                        val i = ctx.packageManager.getLaunchIntentForPackage(apps[name]!!)
                        if (i != null) { start(i); Reply("phone", "$name खोल दिया।") }
                        else Reply("phone", "$name इस फ़ोन में नहीं है।", false)
                    }
                    else -> Reply("phone", "कौन सा ऐप खोलूँ? अभी ये हैं: ${apps.keys.joinToString()}।", false)
                }
            }

            Regex("call|phone laga|फोन लगा|कॉल").containsMatchIn(q) -> {
                if (!allowed("call")) return blocked("call", "कॉल")
                if (number == null) Reply("call", "नंबर बोलिए, जैसे: कॉल 98XXXXXXXX।", false)
                else { start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))); Reply("call", "डायलर खोल दिया। कॉल का बटन आप दबाइए।") }
            }

            Regex("sms|message|मैसेज|संदेश").containsMatchIn(q) -> {
                if (!allowed("msg")) return blocked("msg", "मैसेज")
                start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + (number ?: ""))))
                Reply("msg", "मैसेज की स्क्रीन खोल दी। भेजना आप दबाइए।")
            }

            Regex("timer|टाइमर").containsMatchIn(q) -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                val m = Regex("(\\d+)").find(q)?.value?.toIntOrNull() ?: 5
                start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, m * 60).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                Reply("phone", "$m मिनट का टाइमर लग गया।")
            }

            Regex("back|peeche|वापस|पीछे").containsMatchIn(q) -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y() else { a11y.goBack(); Reply("phone", "पीछे गया।") }
            }
            Regex("home|ghar|होम").containsMatchIn(q) -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y() else { a11y.goHome(); Reply("phone", "होम पर आ गया।") }
            }
            Regex("notification|नोटिफ").containsMatchIn(q) -> {
                if (!allowed("phone")) return blocked("phone", "फ़ोन कंट्रोल")
                if (a11y == null) needA11y() else { a11y.openNotifications(); Reply("phone", "नोटिफ़िकेशन खोल दिए।") }
            }

            else -> Reply("manager", "यह काम अभी मुझे नहीं आता। अभी मैं ये कर सकता हूँ: ऐप खोलना, कॉल और मैसेज की स्क्रीन, टाइमर, बैटरी, समय, याद रखना, पीछे, होम।", false)
        }
    }

    private fun needA11y(): Reply {
        start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return Reply("phone", "पहले सेटिंग में JARVIS को चालू कीजिए। सेटिंग खोल दी है।", false)
    }
}
