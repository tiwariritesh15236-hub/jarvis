package com.ritesh.jarvis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification

/**
 * Phone chalu hone ya app update ke baad Android mic wali service seedhe shuru karne nahi deta.
 * Isliye ek notification dikhata hai: use dabate hi JARVIS sunna shuru kar deta hai.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Jarvis.init(ctx)
        if (!Jarvis.handsFree) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("jarvis_boot", "JARVIS शुरू करें", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, "jarvis_boot")
            .setContentTitle("JARVIS AI")
            .setContentText("सुनना शुरू करने के लिए यहाँ दबाइए")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2, n)
    }
}
