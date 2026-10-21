package com.example.agen

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Gemini {
    private const val MODEL = "gemini-2.5-flash"

    /** Kirim prompt, kembalikan teks JSON dari model. */
    fun ask(apiKey: String, prompt: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("temperature", 0.2))
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.setRequestProperty("Content-Type", "application/json")
        c.connectTimeout = 20000; c.readTimeout = 60000
        c.doOutput = true
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code !in 200..299) throw RuntimeException("API $code: ${txt.take(200)}")
        return JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }
}
