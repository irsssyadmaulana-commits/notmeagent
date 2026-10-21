package com.example.agen

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private lateinit var goal: EditText
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val prefs = getSharedPreferences("agen", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad) }

        val title = TextView(this).apply { text = "NotMe Agent"; textSize = 26f; setTypeface(null, android.graphics.Typeface.BOLD) }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 14f; setPadding(0, pad, 0, 4) }
        val key = EditText(this).apply {
            hint = "API key Gemini (gratis, aistudio.google.com)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
        }
        goal = EditText(this).apply { hint = "Contoh: buka YouTube dan cari lagu santai"; minLines = 2 }
        val status = TextView(this).apply { text = "Siap."; setPadding(0, pad, 0, 0) }
        val b1 = Button(this).apply { text = "1. Aktifkan Aksesibilitas"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        val b2 = Button(this).apply { text = "2. Jalankan agen" }
        val mic = Button(this).apply {
            text = "🎤 Ucapkan tugas"
            setOnClickListener {
                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                try { startActivityForResult(i, 1) }
                catch (e: ActivityNotFoundException) { Toast.makeText(this@MainActivity, "Input suara tidak tersedia di HP ini.", Toast.LENGTH_LONG).show() }
            }
        }
        val b3 = Button(this).apply { text = "Stop"; setOnClickListener { AgentService.instance?.stop() } }

        b2.setOnClickListener {
            val s = AgentService.instance
            when {
                s == null -> Toast.makeText(this, "Aktifkan dulu layanan NotMe Agent di Aksesibilitas.", Toast.LENGTH_LONG).show()
                key.text.isBlank() || goal.text.isBlank() -> Toast.makeText(this, "Isi API key dan tugas.", Toast.LENGTH_SHORT).show()
                else -> {
                    prefs.edit().putString("key", key.text.toString().trim()).apply()
                    AgentService.onStatus = { m -> runOnUiThread { status.text = m } }
                    s.start(goal.text.toString(), key.text.toString().trim())
                    status.text = "Berjalan…"
                }
            }
        }
        listOf(title, label("API key"), key, label("Tugas untuk agen"), goal, mic, b1, b2, b3, status).forEach { col.addView(it) }
        setContentView(ScrollView(this).apply { addView(col) })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1 && res == RESULT_OK)
            data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { goal.setText(it) }
    }
}
