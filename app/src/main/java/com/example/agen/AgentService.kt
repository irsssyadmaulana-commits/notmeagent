package com.example.agen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class AgentService : AccessibilityService() {

    companion object {
        var instance: AgentService? = null
        @Volatile var running = false
        var onStatus: ((String) -> Unit)? = null
        const val MAX_STEPS = 25
    }

    private val nodes = ArrayList<AccessibilityNodeInfo>()
    private var overlay: Button? = null
    private val ui = Handler(Looper.getMainLooper())

    private fun showStop() = ui.post {
        if (overlay != null) return@post
        val btn = Button(this).apply {
            text = "⏹ STOP"; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xE6D32F2F.toInt())
            setOnClickListener { stop() }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; y = 250 }
        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(btn, lp)
        overlay = btn
    }

    private fun hideStop() = ui.post {
        overlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        overlay = null
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { running = false }
    override fun onDestroy() { running = false; hideStop(); instance = null; super.onDestroy() }

    private fun say(s: String) = onStatus?.invoke(s)

    fun stop() { running = false }

    fun start(goal: String, apiKey: String) {
        if (running) return
        running = true
        showStop()
        Thread {
            val history = ArrayList<String>()
            try {
                for (step in 1..MAX_STEPS) {
                    if (!running) { say("Dihentikan."); return@Thread }
                    val screen = dumpScreen()
                    val prompt = buildPrompt(goal, history, screen)
                    say("Langkah $step: berpikir…")
                    val a = JSONObject(Gemini.ask(apiKey, prompt))
                    val act = a.optString("action")
                    val reason = a.optString("reason")
                    say("Langkah $step: $act ${a.optString("index")} ${a.optString("text")}\n$reason")
                    history.add("$act index=${a.optString("index")} text=${a.optString("text")}")
                    if (act == "done") { say("Selesai: ${a.optString("text")}"); return@Thread }
                    exec(a)
                    Thread.sleep(1800)
                }
                say("Berhenti: batas $MAX_STEPS langkah tercapai.")
            } catch (e: Exception) {
                say("Error: ${e.message}")
            } finally { running = false; hideStop() }
        }.start()
    }

    private fun buildPrompt(goal: String, history: List<String>, screen: String) = """
You control an Android phone through accessibility actions. Goal from the user: "$goal"
Each step, look at the current screen elements and choose ONE action. Reply ONLY with JSON:
{"action":"click|type|scroll_down|scroll_up|back|home|open_app|wait|done","index":<element number>,"text":"<text to type, app name, or final message>","reason":"<short>"}
Rules: use "open_app" with the app name in "text" to launch apps. Use "type" with index of an input field. Use "done" when the goal is complete or impossible (text = summary).
NEVER enter passwords, payment details or confirm purchases/transfers; if needed, use "done" and explain.
Previous actions: ${history.takeLast(8)}
Current screen:
$screen
""".trimIndent()

    private fun dumpScreen(): String {
        nodes.clear()
        val root = rootInActiveWindow ?: return "(layar tidak terbaca)"
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            val t = n.text?.toString().orEmpty(); val d = n.contentDescription?.toString().orEmpty()
            if (n.isVisibleToUser && (n.isClickable || n.isEditable || n.isScrollable || t.isNotEmpty() || d.isNotEmpty())) {
                sb.append("[${nodes.size}] ${n.className?.toString()?.substringAfterLast('.')} ")
                if (t.isNotEmpty()) sb.append("\"${t.take(80)}\" ")
                if (d.isNotEmpty()) sb.append("desc=\"${d.take(60)}\" ")
                if (n.isClickable) sb.append("clickable ")
                if (n.isEditable) sb.append("editable ")
                if (n.isScrollable) sb.append("scrollable ")
                sb.append('\n'); nodes.add(n)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return sb.toString().take(6000)
    }

    private fun exec(a: JSONObject) {
        val idx = a.optInt("index", -1)
        val text = a.optString("text")
        when (a.optString("action")) {
            "click" -> nodes.getOrNull(idx)?.let { clickNode(it) }
            "type" -> nodes.getOrNull(idx)?.let {
                it.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val b = Bundle(); b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
            }
            "scroll_down" -> swipe(0.7f, 0.3f)
            "scroll_up" -> swipe(0.3f, 0.7f)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "open_app" -> openApp(text)
        }
    }

    private fun clickNode(n: AccessibilityNodeInfo) {
        var c: AccessibilityNodeInfo? = n
        while (c != null) {
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            c = c.parent
        }
        val r = Rect(); n.getBoundsInScreen(r)   // cadangan: ketuk koordinat
        gesture(r.exactCenterX(), r.exactCenterY(), r.exactCenterX(), r.exactCenterY(), 80)
    }

    private fun swipe(from: Float, to: Float) {
        val m = resources.displayMetrics
        gesture(m.widthPixels / 2f, m.heightPixels * from, m.widthPixels / 2f, m.heightPixels * to, 350)
    }

    private fun gesture(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long) {
        val p = Path(); p.moveTo(x1, y1); if (x1 != x2 || y1 != y2) p.lineTo(x2, y2)
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build(), null, null)
    }

    private fun openApp(name: String) {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hit = pm.queryIntentActivities(q, 0).firstOrNull {
            it.loadLabel(pm).toString().contains(name, ignoreCase = true)
        } ?: return
        startActivity(pm.getLaunchIntentForPackage(hit.activityInfo.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
