package com.vanta.remote

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.vanta.remote.network.UdpClient
import com.vanta.remote.ui.screens.MainScreen
import com.vanta.remote.ui.theme.VRemoteTheme

class MainActivity : ComponentActivity() {

    private lateinit var udpClient: UdpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep phone screen awake during remote control session
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        udpClient = UdpClient(lifecycleScope)

        setContent {
            VRemoteTheme {
                MainScreen(udpClient = udpClient)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        udpClient.disconnect()
    }
}
