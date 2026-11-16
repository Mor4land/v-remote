package com.vanta.remote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.vanta.remote.network.UdpClient
import com.vanta.remote.network.VideoStreamClient
import com.vanta.remote.ui.screens.MainScreen
import com.vanta.remote.ui.theme.VRemoteTheme

class MainActivity : ComponentActivity() {

    private lateinit var udpClient: UdpClient
    private lateinit var videoClient: VideoStreamClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake during remote session
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        udpClient = UdpClient(lifecycleScope)
        videoClient = VideoStreamClient(lifecycleScope)

        setContent {
            VRemoteTheme {
                MainScreen(
                    udpClient = udpClient,
                    videoClient = videoClient
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        udpClient.disconnect()
        videoClient.disconnect()
    }
}
