package com.ritesh.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings

/** Hindi/Hinglish command ko asli Android action mein badalta hai. Jo nahi ho sakta, saaf bolta hai. */
class CommandRouter(private val ctx: Context) {
    private val apps = mapOf(
        "youtube" to "com.google.android.youtube", "whatsapp" to "com.whatsapp",
        "gmail" to "com.google.android.gm", "maps" to "com.google.android.apps.maps",
        "camera" to "", "chrome" to "com.android.chrome"
    )

    private fun start(i: Intent) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }

    fun handle(raw: String): String {
        val q = raw.lowercase().trim()
        val a11y = JarvisAccessibilityService.instance
        return when {
            Regex("battery|बैटरी").containsMatchIn(q) -> {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                "Battery ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent hai."
            }
            Regex("kholo|khol|open|खोल").containsMatchIn(q) -> {
                val name = apps.keys.firstOrNull { q.contains(it) }
                if (name == "camera") { start(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)); "Camera khol diya." }
                else if (name != null) {
                    val i = ctx.packageManager.getLaunchIntentForPackage(apps[name]!!)
                    if (i != null) { start(i); "$name khol diya." } else "$name is phone mein installed nahi hai."
                } else "Kaunsa app kholna hai? Abhi ye hain: ${apps.keys.joinToString()}."
            }
            Regex("call|phone laga|फोन लगा").containsMatchIn(q) -> {
                val num = Regex("\\+?\\d[\\d ]{6,}").find(q)?.value?.replace(" ", "")
                if (num == null) "Number bolein, jaise: call 98XXXXXXXX."
                else { start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))); "Dialer khol diya. Call aap dabayenge." }
            }
            Regex("sms|message bhej").containsMatchIn(q) -> {
                val num = Regex("\\+?\\d[\\d ]{6,}").find(q)?.value?.replace(" ", "") ?: ""
                start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", ""))
                "Message screen khol di. Bhejna aap dabayenge."
            }
            Regex("timer").containsMatchIn(q) -> {
                val m = Regex("(\\d+)").find(q)?.value?.toIntOrNull() ?: 5
                start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, m * 60).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                "$m minute ka asli timer lag gaya."
            }
            Regex("back|peeche|वापस").containsMatchIn(q) -> a11y?.let { it.goBack(); "Peeche gaya." } ?: needA11y()
            Regex("home|ghar").containsMatchIn(q) -> a11y?.let { it.goHome(); "Home par aa gaya." } ?: needA11y()
            Regex("notification").containsMatchIn(q) -> a11y?.let { it.openNotifications(); "Notification panel khol diya." } ?: needA11y()
            else -> "Ye command abhi mere paas nahi hai. Server se jodne par Claude jawab dega."
        }
    }

    private fun needA11y(): String {
        start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        return "Pehle Accessibility mein JARVIS ko ON kijiye. Settings khol di."
    }
}
