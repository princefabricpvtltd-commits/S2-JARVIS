package com.s2jarvis.ultra

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Spinner
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var answer: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var serverUrl: EditText
    private lateinit var orb: JarvisOrbView
    private lateinit var voiceLanguage: Spinner
    private val languageCodes = listOf("hi-IN", "en-IN", "pa-IN")
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val io = Executors.newSingleThreadExecutor()
    private val permissionCode = 501
    private var wakeReceiverRegistered = false
    private var wakeEnabled = false
    private val wakeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD) {
                wakeEnabled = false
                prefs.edit().putBoolean("wake_enabled", false).apply()
                findViewById<Button>(R.id.wakeButton).text = "ENABLE HEY S2"
                status.text = "● HEY S2 DETECTED • LISTENING"
                listen()
            }
        }
    }
    private val history = JSONArray()
    private val prefs by lazy { getSharedPreferences("s2_jarvis", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        answer = findViewById(R.id.answerText)
        status = findViewById(R.id.statusText)
        input = findViewById(R.id.questionInput)
        serverUrl = findViewById(R.id.serverUrl)
        orb = findViewById(R.id.jarvisOrb)
        voiceLanguage = findViewById(R.id.voiceLanguage)
        voiceLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Hindi • हिन्दी", "English • India", "Punjabi • ਪੰਜਾਬੀ"))
        voiceLanguage.setSelection(prefs.getInt("voice_language_index", 0).coerceIn(0, 2))
        voiceLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt("voice_language_index", position).apply()
                applyTtsLanguage()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        serverUrl.setText(prefs.getString("server_url", ""))
        tts = TextToSpeech(this, this)

        findViewById<Button>(R.id.askButton).setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isNotEmpty()) process(q)
        }
        findViewById<Button>(R.id.micButton).setOnClickListener { listen() }
        wakeEnabled = prefs.getBoolean("wake_enabled", false)
        findViewById<Button>(R.id.wakeButton).text = if (wakeEnabled) "HEY S2 IS ON • TAP TO DISABLE" else "ENABLE HEY S2 • BACKGROUND WAKE WORD"
        findViewById<Button>(R.id.wakeButton).setOnClickListener { toggleWakeWord() }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, WakeWordService::class.java))
            wakeEnabled = false
            prefs.edit().putBoolean("wake_enabled", false).apply()
            findViewById<Button>(R.id.wakeButton).text = "ENABLE HEY S2"

            tts?.stop()
            recognizer?.cancel()
            orb.setActive(false)
            status.text = "● VOICE STOPPED"
        }
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), permissionCode)
        if (intent?.getBooleanExtra(WakeWordService.EXTRA_WAKE_DETECTED, false) == true) {
            window.decorView.post { listen() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!wakeReceiverRegistered) {
            val filter = android.content.IntentFilter(WakeWordService.ACTION_WAKE_WORD)
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(wakeReceiver, filter, RECEIVER_NOT_EXPORTED)
            else registerReceiver(wakeReceiver, filter)
            wakeReceiverRegistered = true
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra(WakeWordService.EXTRA_WAKE_DETECTED, false) == true) {
            window.decorView.post { listen() }
        }
    }

    override fun onPause() {
        if (wakeReceiverRegistered) {
            unregisterReceiver(wakeReceiver)
            wakeReceiverRegistered = false
        }
        super.onPause()
    }

    private fun toggleWakeWord() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionCode)
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), permissionCode)
            return
        }
        if (wakeEnabled) {
            stopService(Intent(this, WakeWordService::class.java))
            wakeEnabled = false
            prefs.edit().putBoolean("wake_enabled", false).apply()
            findViewById<Button>(R.id.wakeButton).text = "ENABLE HEY S2"
            status.text = "● WAKE WORD DISABLED"
            return
        }
        try {
            val serviceIntent = Intent(this, WakeWordService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            wakeEnabled = true
            prefs.edit().putBoolean("wake_enabled", true).apply()
            findViewById<Button>(R.id.wakeButton).text = "HEY S2 IS ON • TAP TO DISABLE"
            status.text = "● WAKE WORD ACTIVE • SAY HEY S2"
        } catch (e: Exception) {
            reply("Hey S2 start nahi ho paaya: ${e.message ?: "permission required"}")
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            applyTtsLanguage()
            tts?.setSpeechRate(0.96f)
            tts?.setPitch(1.02f)
            status.text = "● SYSTEM READY • VOICE ONLINE"
        } else {
            status.text = "● SYSTEM READY • TTS UNAVAILABLE"
        }
    }

    private fun applyTtsLanguage() {
        if (!::voiceLanguage.isInitialized) return
        val locale = when (voiceLanguage.selectedItemPosition) {
            1 -> Locale("en", "IN")
            2 -> Locale("pa", "IN")
            else -> Locale("hi", "IN")
        }
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.getDefault())
        }
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            reply("Speech recognition service nahi mil rahi. Aap type karke command de sakte hain.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionCode)
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "● LISTENING..."; orb.setActive(true) }
                override fun onBeginningOfSpeech() { status.text = "● HEARING YOU" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "● THINKING..."; orb.setActive(false) }
                override fun onError(error: Int) {
                    orb.setActive(false)
                    status.text = "● VOICE ERROR $error • TAP MIC TO RETRY"
                }
                override fun onResults(results: Bundle?) {
                    orb.setActive(false)
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    input.setText(words)
                    if (words.isNotBlank()) process(words) else status.text = "● DID NOT CATCH THAT"
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) answer.text = partial
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val selectedLanguage = languageCodes[voiceLanguage.selectedItemPosition.coerceIn(0, 2)]
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, selectedLanguage)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "S2 JARVIS se baat karein")
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            orb.setActive(false)
            reply("Voice start nahi ho paayi: ${e.message ?: "unknown error"}")
        }
    }

    private fun process(question: String) {
        val local = localCommand(question)
        if (local != null) {
            reply(local)
            return
        }
        val endpoint = serverUrl.text.toString().trim()
        prefs.edit().putString("server_url", endpoint).apply()
        if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://")) {
            reply("Yeh command local mode mein nahi mili. AI chat ke liye AI server URL set karein.")
            return
        }
        status.text = "● JARVIS AI IS THINKING..."
        orb.setActive(true)
        io.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val payload = JSONObject().put("message", question).put("history", history).toString()
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val detail = try { JSONObject(body).optString("error", "Server error $code") } catch (_: Exception) { "Server error $code" }
                    throw Exception(detail)
                }
                val text = JSONObject(body).optString("reply", "AI server returned no reply.")
                runOnUiThread {
                    history.put(JSONObject().put("role", "user").put("content", question))
                    history.put(JSONObject().put("role", "assistant").put("content", text))
                    while (history.length() > 12) history.remove(0)
                    orb.setActive(false)
                    reply(text)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    orb.setActive(false)
                    reply("AI connection nahi ho paaya: ${e.message ?: "network error"}. Server, internet aur URL check karein.")
                }
            } finally { conn?.disconnect() }
        }
    }

    private fun localCommand(raw: String): String? {
        val q = raw.lowercase(Locale.ROOT).trim()
        val number = Regex("(?:call|dial|phone|कॉल|लगाओ|डायल)\\s*(?:to\\s*)?([+0-9][0-9 -]{7,})").find(q)?.groupValues?.get(1)?.replace(" ", "")?.replace("-", "")
        return when {
            listOf("hello", "hi", "namaste", "sat sri akal", "ਸਤ ਸ੍ਰੀ ਅਕਾਲ", "नमस्ते").any { q.contains(it) } ->
                "Namaste ji! Main S2 JARVIS hoon. Bataiye, main aapki kya madad karun?"
            listOf("time", "समय", "kitne baje", "वक्त").any { q.contains(it) } ->
                "Abhi " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) + " baj rahe hain."
            listOf("date", "tarikh", "तारीख", "ਅੱਜ ਦੀ ਤਾਰੀਖ", "आज कौन सा दिन").any { q.contains(it) } ->
                "Aaj " + SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("hi", "IN")).format(Date()) + " hai."
            q.contains("your name") || q.contains("tumhara naam") || q.contains("naam kya") ->
                "Mera naam S2 JARVIS hai. Main aapka personal AI assistant hoon."
            q.contains("who are you") || q.contains("kaun ho") -> "Main S2 JARVIS hoon, aapka personal assistant."
            number != null -> openDialer(number)
            q.contains("open youtube") || q.contains("youtube kholo") || q.contains("यूट्यूब खोलो") -> launchApp("com.google.android.youtube", "YouTube")
            q.contains("open whatsapp") || q.contains("whatsapp kholo") || q.contains("व्हाट्सएप खोलो") -> launchApp("com.whatsapp", "WhatsApp")
            q.contains("open camera") || q.contains("camera kholo") || q.contains("कैमरा खोलो") -> launchCamera()
            q.contains("open chrome") || q.contains("chrome kholo") || q.contains("क्रोम खोलो") -> launchApp("com.android.chrome", "Chrome")
            q.contains("open maps") || q.contains("maps kholo") || q.contains("map kholo") || q.contains("मैप खोलो") -> launchMaps()
            q.contains("open settings") || q.contains("settings kholo") || q.contains("सेटिंग खोलो") -> launchSettings()
            q.contains("set alarm") || q.contains("alarm lagao") || q.contains("अलार्म लगाओ") -> setAlarm()
            q.startsWith("search ") || q.contains("google par search") || q.contains("google search") || q.contains("सर्च करो") -> googleSearch(raw)
            q.startsWith("open http") || q.startsWith("website ") -> openWebsite(raw.substringAfter(' ').trim())
            else -> null
        }
    }

    private fun launchApp(pkg: String, label: String): String = try {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) { startActivity(i); "$label khol raha hoon." } else "$label app phone par install nahi hai."
    } catch (_: Exception) { "$label open nahi ho paaya." }

    private fun launchCamera(): String = try {
        startActivity(Intent("android.media.action.IMAGE_CAPTURE")); "Camera khol raha hoon."
    } catch (_: Exception) { "Camera open nahi ho paaya." }

    private fun launchMaps(): String = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))
        "Maps khol raha hoon."
    } catch (_: Exception) { "Maps app open nahi ho paayi." }

    private fun launchSettings(): String = try {
        startActivity(Intent(Settings.ACTION_SETTINGS)); "Phone settings khol raha hoon."
    } catch (_: Exception) { "Settings open nahi ho paayi." }

    private fun openDialer(number: String): String = try {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        "Dialer mein $number dikha raha hoon. Call karne se pehle aap confirm kar sakte hain."
    } catch (_: Exception) { "Dialer open nahi ho paaya." }

    private fun setAlarm(): String = try {
        startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_MESSAGE, "S2 JARVIS Alarm"))
        "Alarm screen khol raha hoon. Wahan time confirm karke save karein."
    } catch (_: Exception) { "Alarm app available nahi hai." }

    private fun openWebsite(value: String): String = try {
        val url = if (value.startsWith("http")) value else "https://$value"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); "Website khol raha hoon."
    } catch (_: Exception) { "Website open nahi ho paayi." }

    private fun googleSearch(q: String): String = try {
        val cleaned = q.replace(Regex("(?i)^(search|google search)\\s*"), "")
            .replace("google par search", "").replace("सर्च करो", "").trim()
        val url = "https://www.google.com/search?q=" + URLEncoder.encode(cleaned.ifBlank { q }, "UTF-8")
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        "Google par search kar raha hoon."
    } catch (_: Exception) { "Google search open nahi ho paayi." }

    private fun reply(text: String) {
        answer.text = text
        status.text = "● S2 JARVIS • READY"
        orb.setActive(false)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "S2_REPLY")
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == permissionCode) status.text = if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            "● MICROPHONE READY" else "● MIC PERMISSION DENIED • TYPE COMMANDS"
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        orb.setActive(false)
        io.shutdownNow()
        super.onDestroy()
    }
}
