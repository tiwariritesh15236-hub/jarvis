package com.ritesh.jarvis

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Naya message aate hi uski khabar bolta hai. Settings mein "Notification access" dena padta hai. */
class MessageNotifService : NotificationListenerService() {

    private var lastKey = ""
    private var lastAt = 0L

    private val msgApps = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
        "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
        "com.facebook.orca", "com.instagram.android"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Jarvis.init(applicationContext)
        if (!Jarvis.announceMessages) return
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val isMsg = n.category == Notification.CATEGORY_MESSAGE || sbn.packageName in msgApps
        if (!isMsg) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty()) return
        val key = sbn.key + "|" + text
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < 10000) return
        lastKey = key
        lastAt = now
        val msg = if (Jarvis.announceBody && text.isNotEmpty()) "$title का मैसेज आया: ${text.take(140)}" else "$title का मैसेज आया।"
        Jarvis.announce(msg, "inbox")
    }
}
