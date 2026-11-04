package com.ritesh.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Online AI dimaag. Aap apni chaabi (API key) settings mein daalte hain: Gemini, Claude ya ChatGPT.
 * Sawaal ka jawab dega, aur kathin hukm ko chhote phone-kaamon mein baant dega.
 * Internet ya chaabi na ho to ye chup rehta hai aur saaf bata deta hai.
 */
object Brain {
    private val main = Handler(Looper.getMainLooper())
    private val history = ArrayList<Pair<String, String>>()

    class Result(val ok: Boolean, val say: String, val cmds: List<String>)

    private fun prefs() = Jarvis.app.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun provider(): String = prefs().getString("ai_provider", "gemini") ?: "gemini"

    fun providerName(): String = when (provider()) {
        "claude" -> "Claude"
        "openai" -> "ChatGPT"
        else -> "Gemini"
    }

    fun setProvider(p: String) { prefs().edit().putString("ai_provider", p).apply() }

    fun key(): String = prefs().getString("ai_key_" + provider(), "") ?: ""

    private fun defaultModel(): String = when (provider()) {
        "claude" -> "claude-haiku-5-5"
        "openai" -> "gpt-4o-mini"
        else -> "gemini-2.5-flash"
    }

    fun model(): String {
        val m = prefs().getString("ai_model_" + provider(), "") ?: ""
        return if (m.isBlank()) defaultModel() else m
    }

    fun save(key: String, model: String) {
        prefs().edit()
            .putString("ai_key_" + provider(), key.trim())
            .putString("ai_model_" + provider(), model.trim())
            .apply()
    }

    fun ready(): Boolean = key().isNotBlank()

    fun forget() { history.clear() }

    private fun system(): String {
        val now = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.US).format(Date())
        return """
You are JARVIS, a voice assistant living inside the user's Android phone. The user speaks Hindi, Hinglish or English. Reply in the user's language (default: Hindi in Devanagari). Your reply is spoken aloud, so keep it short (1 to 3 sentences), with no markdown, no lists and no emoji.
Be honest. Never say you did something unless it is in "cmds". You cannot make payments. You cannot control other apps except through the commands below. You do not know live facts (news, prices, scores) unless the user tells you; say so instead of guessing. Never claim to be human.
Reply with ONLY one JSON object: {"say": "<what to speak>", "cmds": ["<command>", ...]}. "cmds" is optional and empty when the user only asked a question. At most 5 commands, run in order by the phone. Each command must be a short Hindi sentence in exactly one of these forms:
- "<नाम या नंबर> को कॉल करो" (opens the dialer, the user presses call)
- "<नाम या नंबर> को मैसेज भेजो कि <text>" (opens the SMS screen with the text, the user presses send)
- "<ऐप> खोलो" (youtube, whatsapp, gmail, maps, chrome, instagram, facebook, telegram, spotify, कैमरा)
- "यूट्यूब पर <गाना या वीडियो> चलाओ"
- "<जगह> का रास्ता दिखाओ"
- "<बात> गूगल पर खोजो"
- "टॉर्च चालू करो" or "टॉर्च बंद करो"
- "आवाज़ बढ़ाओ" or "आवाज़ कम करो" or "आवाज़ म्यूट करो"
- "सुबह <N> बजे का अलार्म लगाओ" or "<N> मिनट का टाइमर लगाओ"
- "नोट लिखो <text>" or "मेरे नोट पढ़ो"
- "याद रखो <text>" or "क्या याद है"
- "नीचे स्क्रॉल करो", "ऊपर स्क्रॉल करो", "स्क्रीनशॉट लो", "फ़ोन लॉक करो", "<बटन का नाम> पर क्लिक करो", "पीछे जाओ", "होम पर जाओ", "नोटिफ़िकेशन खोलो"
- "बैटरी कितनी है", "वाई-फाई सेटिंग खोलो"
If a command fully answers the request, "say" can be short, like "ठीक है, कर रहा हूँ।". If the user asks for something outside these commands (building apps, uploading, payments), say honestly that you cannot do that yet.
Current date and time: $now
""".trim()
    }

    fun ask(text: String, cb: (Result) -> Unit) {
        history.add(Pair("user", text))
        while (history.size > 12) history.removeAt(0)
        val snap = ArrayList(history)
        while (snap.isNotEmpty() && snap[0].first != "user") snap.removeAt(0)
        val prov = provider()
        val k = key()
        val m = model()
        Thread {
            val res = try {
                call(prov, k, m, snap)
            } catch (e: Exception) {
                Result(false, "AI से जवाब नहीं आ पाया। " + (e.message ?: "कोई गड़बड़ हुई।"), emptyList())
            }
            main.post {
                if (res.ok) history.add(Pair("assistant", if (res.say.isBlank()) "ठीक है।" else res.say))
                cb(res)
            }
        }.start()
    }

    private fun call(prov: String, key: String, model: String, hist: List<Pair<String, String>>): Result {
        val sys = system()
        val out: String = when (prov) {
            "claude" -> {
                val msgs = JSONArray()
                for (h in hist) msgs.put(JSONObject().put("role", h.first).put("content", h.second))
                val body = JSONObject().put("model", model).put("max_tokens", 600).put("system", sys).put("messages", msgs)
                val r = post("https://api.anthropic.com/v1/messages", body.toString(),
                    mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
                JSONObject(r).getJSONArray("content").getJSONObject(0).getString("text")
            }
            "openai" -> {
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content", sys))
                for (h in hist) msgs.put(JSONObject().put("role", h.first).put("content", h.second))
                val body = JSONObject().put("model", model).put("messages", msgs)
                val r = post("https://api.openai.com/v1/chat/completions", body.toString(),
                    mapOf("Authorization" to "Bearer $key"))
                JSONObject(r).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
            else -> {
                val contents = JSONArray()
                for (h in hist) {
                    val role = if (h.first == "user") "user" else "model"
                    contents.put(JSONObject().put("role", role)
                        .put("parts", JSONArray().put(JSONObject().put("text", h.second))))
                }
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                    .put("contents", contents)
                val r = post("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent",
                    body.toString(), mapOf("x-goog-api-key" to key))
                JSONObject(r).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }
        }
        return parse(out)
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 45000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val txt = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw RuntimeException(
                when (code) {
                    400 -> "अनुरोध सही नहीं था। मॉडल का नाम जाँचिए।"
                    401, 403 -> "चाबी (API key) गलत है या उसे अनुमति नहीं है।"
                    404 -> "मॉडल का नाम सही नहीं है। सेटिंग में नाम बदलिए।"
                    429 -> "AI की सीमा या भुगतान की दिक्कत है।"
                    else -> "सर्वर की गड़बड़ ($code)।"
                }
            )
        }
        return txt
    }

    private fun parse(raw: String): Result {
        val t = raw.trim()
        val s = t.indexOf('{')
        val e = t.lastIndexOf('}')
        if (s >= 0 && e > s) {
            try {
                val o = JSONObject(t.substring(s, e + 1))
                val say = o.optString("say", "")
                val arr = o.optJSONArray("cmds")
                val cmds = ArrayList<String>()
                if (arr != null) for (i in 0 until arr.length()) cmds.add(arr.optString(i))
                return Result(true, say, cmds.filter { it.isNotBlank() })
            } catch (x: Exception) {
                // seedha text hi bol dete hain
            }
        }
        return Result(true, t, emptyList())
    }
}
