package com.example.socialroboticslab.http

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.socialroboticslab.R
import com.example.socialroboticslab.util.PrefsManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Ch2 對應功能：HTTP Chat
 * 透過 POST /api/chat 和 FastAPI 後端對話。
 */
class HttpChatActivity : AppCompatActivity() {

    private lateinit var tvChat: TextView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnReset: Button
    private lateinit var progressBar: ProgressBar

    private val client = OkHttpClient()
    private val chatHistory = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_http_chat)

        supportActionBar?.title = "HTTP Chat (Ch2)"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvChat = findViewById(R.id.tvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnReset = findViewById(R.id.btnReset)
        progressBar = findViewById(R.id.progressBar)

        btnSend.setOnClickListener { sendMessage() }
        btnReset.setOnClickListener { resetChat() }
    }

    private fun sendMessage() {
        val message = etMessage.text.toString().trim()
        if (message.isEmpty()) return

        appendChat("You: $message")
        etMessage.text.clear()
        setLoading(true)

        val baseUrl = PrefsManager.getHttpUrl(this).trimEnd('/')
        val url = "$baseUrl/api/chat"

        val json = JSONObject().apply {
            put("message", message)
            put("user_id", "android_user")
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoading(false)
                    appendChat("Error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                runOnUiThread {
                    setLoading(false)
                    try {
                        val result = JSONObject(responseBody)
                        val reply = result.optString("reply", responseBody)
                        appendChat("Bot: $reply")
                    } catch (e: Exception) {
                        appendChat("Bot: $responseBody")
                    }
                }
            }
        })
    }

    private fun resetChat() {
        chatHistory.clear()
        tvChat.text = ""

        val baseUrl = PrefsManager.getHttpUrl(this).trimEnd('/')
        val url = "$baseUrl/api/reset"
        val body = JSONObject().apply {
            put("user_id", "android_user")
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HttpChat", "Reset failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun appendChat(line: String) {
        chatHistory.appendLine(line)
        tvChat.text = chatHistory.toString()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnSend.isEnabled = !loading
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
