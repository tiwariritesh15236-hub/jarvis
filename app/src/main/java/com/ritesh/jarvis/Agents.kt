package com.ritesh.jarvis

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/** live = true matlab ye agent abhi sach mein kaam karta hai. false = abhi bana nahi, jald aayega. */
data class Agent(val id: String, val name: String, val desc: String, val live: Boolean)

object Agents {
    val list = listOf(
        Agent("msg", "मैसेज एजेंट", "नाम या नंबर पर मैसेज लिखकर तैयार रखना। भेजना आप दबाते हैं।", true),
        Agent("call", "कॉल एजेंट", "नाम या नंबर का डायलर खोलना। कॉल का बटन आप दबाते हैं।", true),
        Agent("phone", "फ़ोन कंट्रोल एजेंट", "ऐप खोलना, पीछे, होम, नोटिफ़िकेशन।", true),
        Agent("screen", "स्क्रीन एजेंट", "स्क्रॉल, स्क्रीनशॉट, लॉक, बटन पर क्लिक।", true),
        Agent("time", "समय एजेंट", "अलार्म, टाइमर, समय और तारीख बताना।", true),
        Agent("search", "खोज एजेंट", "Google पर कुछ भी खोजना।", true),
        Agent("nav", "रास्ता एजेंट", "Maps में कहीं का रास्ता दिखाना।", true),
        Agent("music", "संगीत एजेंट", "YouTube पर गाना या वीडियो ढूँढकर चलाना।", true),
        Agent("device", "डिवाइस एजेंट", "टॉर्च, आवाज़ कम-ज़्यादा, वाई-फाई/ब्लूटूथ, बैटरी।", true),
        Agent("notes", "नोट्स एजेंट", "नोट लिखना, पढ़कर सुनाना, मिटाना।", true),
        Agent("calc", "गणित एजेंट", "जोड़, घटाना, गुणा, भाग।", true),
        Agent("memory", "मेमोरी एजेंट", "आपकी बताई बातें याद रखना और बताना।", true),
        Agent("design", "डिज़ाइन एजेंट", "पोस्टर, लोगो, ऐप स्क्रीन।", false),
        Agent("dev", "डेवलपर एजेंट", "वेबसाइट और ऐप बनाना, GitHub पर चढ़ाना।", false),
        Agent("audit", "ऑडिट एजेंट", "वेबसाइट, ऐप और अकाउंट स्कैन।", false),
        Agent("media", "वीडियो-ऑडियो एजेंट", "वीडियो और आवाज़ का एडिट।", false)
    )

    fun nameOf(id: String): String =
        if (id == "manager") "Manager" else list.firstOrNull { it.id == id }?.name ?: "Manager"
}

/** Har kaam ka live record. "Kaam" tab yahin se dikhta hai. */
object TaskLog {
    const val RUNNING = "running"
    const val DONE = "done"
    const val FAILED = "failed"
    const val STOPPED = "stopped"

    data class Entry(val time: String, val agent: String, val text: String, val state: String)

    val entries = ArrayList<Entry>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(agent: String, text: String, state: String) {
        entries.add(Entry(fmt.format(Date()), agent, text, state))
        if (entries.size > 100) entries.removeAt(0)
    }
}
