package com.example.socialroboticslab.websocket

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.socialroboticslab.R
import com.example.socialroboticslab.util.PrefsManager
import okhttp3.*
import org.json.JSONObject

/**
 * Ch3 對應功能：WebSocket Audio Streaming
 * 透過 WebSocket 雙向串流音訊，支援語音辨識回傳。
 */
class AudioStreamActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnRecord: Button

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isConnected = false

    private val sampleRate = 24000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_stream)

        supportActionBar?.title = "WebSocket Audio (Ch3)"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvStatus = findViewById(R.id.tvStatus)
        tvTranscript = findViewById(R.id.tvTranscript)
        btnConnect = findViewById(R.id.btnConnect)
        btnRecord = findViewById(R.id.btnRecord)

        btnRecord.isEnabled = false

        btnConnect.setOnClickListener {
            if (!isConnected) connectWebSocket() else disconnectWebSocket()
        }

        btnRecord.setOnClickListener {
            if (!isRecording) startRecording() else stopRecording()
        }
    }

    private fun connectWebSocket() {
        val url = PrefsManager.getWsAudioUrl(this)
        tvStatus.text = "Connecting to $url..."

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runOnUiThread {
                    isConnected = true
                    tvStatus.text = "Connected"
                    btnConnect.text = "Disconnect"
                    btnRecord.isEnabled = true
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread { handleMessage(text) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                runOnUiThread {
                    isConnected = false
                    tvStatus.text = "Connection failed: ${t.message}"
                    btnConnect.text = "Connect"
                    btnRecord.isEnabled = false
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    isConnected = false
                    tvStatus.text = "Disconnected"
                    btnConnect.text = "Connect"
                    btnRecord.isEnabled = false
                }
            }
        })
    }

    private fun disconnectWebSocket() {
        stopRecording()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")
            when (type) {
                "transcript" -> {
                    val transcript = json.optString("text", "")
                    tvTranscript.append("STT: $transcript\n")
                }
                "audio_response" -> {
                    val audioData = json.optString("audio", "")
                    if (audioData.isNotEmpty()) playAudio(audioData)
                    val reply = json.optString("text", "")
                    if (reply.isNotEmpty()) tvTranscript.append("Bot: $reply\n")
                }
                else -> {
                    tvTranscript.append("Server: $text\n")
                }
            }
        } catch (e: Exception) {
            tvTranscript.append("Server: $text\n")
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            tvStatus.text = "Microphone permission required"
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelIn, encoding)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate, channelIn, encoding, bufferSize * 2
        )

        isRecording = true
        btnRecord.text = "Stop Recording"
        tvStatus.text = "Recording..."
        audioRecord?.startRecording()

        Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val encoded = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                    val msg = JSONObject().apply {
                        put("type", "audio")
                        put("audio", encoded)
                        put("sample_rate", sampleRate)
                    }
                    webSocket?.send(msg.toString())
                }
            }
        }.start()
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        runOnUiThread {
            btnRecord.text = "Start Recording"
            tvStatus.text = "Connected"
        }
    }

    private fun playAudio(base64Audio: String) {
        Thread {
            try {
                val audioBytes = Base64.decode(base64Audio, Base64.NO_WRAP)
                val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelOut, encoding)
                val track = AudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelOut)
                            .setEncoding(encoding)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(bufferSize, audioBytes.size))
                    .build()
                track.play()
                track.write(audioBytes, 0, audioBytes.size)
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.e("AudioStream", "Playback error: ${e.message}")
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        webSocket?.close(1000, "Activity destroyed")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
