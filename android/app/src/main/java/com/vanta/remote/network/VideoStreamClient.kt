package com.vanta.remote.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class VideoStreamClient(private val scope: CoroutineScope) {

    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var webSocket: WebSocket? = null

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    fun connect(host: String, port: Int, pin: String) {
        disconnect()
        val url = "ws://$host:$port/ws?token=$pin"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("VRemoteVideo", "WebSocket connected to $url")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bmp = BitmapFactory.decodeByteArray(data, 0, data.size, opts)
                if (bmp != null) {
                    _currentFrame.value = bmp
                    frameCount++
                    val now = System.currentTimeMillis()
                    if (now - lastFpsTime >= 1000) {
                        _fps.value = frameCount
                        frameCount = 0
                        lastFpsTime = now
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("VRemoteVideo", "WebSocket failure: ${t.message}", t)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnect")
        webSocket = null
        _currentFrame.value = null
        _fps.value = 0
    }
}
