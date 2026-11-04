package com.ritesh.jarvis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

/**
 * Phone aane par bolkar batata hai kiska aa raha hai, aur phone katne ke baad batata hai ki baat hui ya chhoot gaya.
 * Note: Android kisi app ko caller se baat karne nahi deta, isliye ye caller se baat nahi karta, sirf aapko khabar deta hai.
 */
class CallReceiver : BroadcastReceiver() {

    companion object {
        private var incoming = false
        private var answered = false
        private var announced = false
        private var number: String? = null
        private var answerAt = 0L
        private val h = Handler(Looper.getMainLooper())

        private fun who(): String = Jarvis.contactName(number) ?: (number ?: "कोई अनजान नंबर")

        private fun announceRing() {
            if (announced || !incoming) return
            announced = true
            Jarvis.announce(who() + " का फ़ोन आ रहा है।", "inbox")
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        Jarvis.init(ctx)
        if (!Jarvis.announceCalls) return
        val st = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val num = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (st) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!incoming) {
                    incoming = true
                    answered = false
                    announced = false
                    number = null
                }
                if (!num.isNullOrBlank()) number = num
                if (number != null) announceRing() else h.postDelayed({ announceRing() }, 1500)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (incoming) { answered = true; answerAt = System.currentTimeMillis() }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (incoming) {
                    val w = who()
                    if (answered) {
                        val sec = ((System.currentTimeMillis() - answerAt) / 1000).toInt()
                        Jarvis.announce("$w से बात हुई, ${sec / 60} मिनट ${sec % 60} सेकंड।", "inbox")
                    } else {
                        Jarvis.announce("$w का फ़ोन छूट गया। मिस्ड कॉल।", "inbox")
                    }
                    incoming = false
                    answered = false
                    announced = false
                    number = null
                }
            }
        }
    }
}
