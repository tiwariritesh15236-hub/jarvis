package com.ritesh.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var router: CommandRouter
    private lateinit var log: TextView
    private lateinit var input: EditText

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        router = CommandRouter(this); tts = TextToSpeech(this, this)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32) }
        root.addView(TextView(this).apply { text = "JARVIS"; textSize = 28f })
        log = TextView(this).apply { textSize = 16f }
        root.addView(ScrollView(this).apply { addView(log) }, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply { hint = "Command likhiye"; setSingleLine() }
        root.addView(input)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(Button(this).apply { text = "Bhejo"; setOnClickListener { run(input.text.toString()); input.setText("") } })
        row.addView(Button(this).apply { text = "Bolo"; setOnClickListener { listen() } })
        row.addView(Button(this).apply { text = "STOP"; setOnClickListener { tts.stop(); log("Ruk gaya.") } })
        root.addView(row)
        setContentView(root)
    }

    override fun onInit(status: Int) { tts.language = Locale("hi", "IN") }
    private fun log(t: String) { log.append(t + "\n\n") }
    private fun run(cmd: String) { if (cmd.isBlank()) return; log("Aap: $cmd"); val r = router.handle(cmd); log("JARVIS: $r"); tts.speak(r, TextToSpeech.QUEUE_FLUSH, null, "j") }
    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        try { startActivityForResult(i, 7) } catch (e: Exception) { log("Voice input is phone par nahi mila.") }
    }
    override fun onActivityResult(rq: Int, rs: Int, d: Intent?) {
        super.onActivityResult(rq, rs, d)
        if (rq == 7 && rs == RESULT_OK) d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { run(it) }
    }
}
