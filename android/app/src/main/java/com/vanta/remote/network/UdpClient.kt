package com.vanta.remote.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UdpClient(private val scope: CoroutineScope) {

    private var socket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private var serverPort: Int = 8766

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _latencyMs = MutableStateFlow(-1)
    val latencyMs: StateFlow<Int> = _latencyMs.asStateFlow()

    private var pingJob: Job? = null
    private var receiveJob: Job? = null
    private var pingSeq = 0
    private val pingTimestamps = HashMap<Int, Long>()

    // Protocol constants
    companion object {
        const val MAGIC_BYTE: Byte = 0x56 // 'V'
        const val TYPE_MOUSE_MOVE_REL: Byte = 0x01
        const val TYPE_MOUSE_MOVE_ABS: Byte = 0x02
        const val TYPE_MOUSE_BUTTON: Byte = 0x03
        const val TYPE_MOUSE_SCROLL: Byte = 0x04
        const val TYPE_KEY_TAP: Byte = 0x05
        const val TYPE_PING: Byte = 0x06
        const val TYPE_PONG: Byte = 0x07
        const val TYPE_AUTH: Byte = 0x08
        const val TYPE_UNICODE_CHAR: Byte = 0x09
    }

    fun connect(host: String, port: Int, pin: Int) {
        disconnect()
        scope.launch(Dispatchers.IO) {
            try {
                val s = DatagramSocket()
                s.soTimeout = 2000
                socket = s
                serverAddress = InetAddress.getByName(host.trim())
                serverPort = port
                _isConnected.value = true

                // Send Auth packet
                sendAuth(pin)

                // Start receive loop for pong
                startReceiveLoop()

                // Start ping loop
                startPingLoop()
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    fun disconnect() {
        pingJob?.cancel()
        receiveJob?.cancel()
        pingJob = null
        receiveJob = null
        socket?.close()
        socket = null
        _isConnected.value = false
        _latencyMs.value = -1
    }

    private fun sendBytes(data: ByteArray) {
        val s = socket ?: return
        val addr = serverAddress ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val packet = DatagramPacket(data, data.size, addr, serverPort)
                s.send(packet)
            } catch (_: Exception) {}
        }
    }

    fun sendAuth(pin: Int) {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTE)
        buf.put(TYPE_AUTH)
        buf.putInt(pin)
        sendBytes(buf.array())
    }

    fun sendMouseMoveRel(dx: Short, dy: Short) {
        val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTE)
        buf.put(TYPE_MOUSE_MOVE_REL)
        buf.putShort(dx)
        buf.putShort(dy)
        sendBytes(buf.array())
    }

    fun sendMouseMoveAbs(rx: Float, ry: Float) {
        val clampedX = (rx.coerceIn(0f, 1f) * 65535f).toInt()
        val clampedY = (ry.coerceIn(0f, 1f) * 65535f).toInt()
        val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTE)
        buf.put(TYPE_MOUSE_MOVE_ABS)
        buf.putShort(clampedX.toShort())
        buf.putShort(clampedY.toShort())
        sendBytes(buf.array())
    }

    fun sendMouseButton(button: Byte, action: Byte) {
        // button: 0=Left, 1=Right, 2=Middle
        // action: 0=Click, 1=Down, 2=Up
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTE)
        buf.put(TYPE_MOUSE_BUTTON)
        buf.put(button)
        buf.put(action)
        sendBytes(buf.array())
    }

    fun sendMouseScroll(deltaY: Byte, deltaX: Byte = 0) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTE)
        buf.put(TYPE_MOUSE_SCROLL)
        buf.put(deltaY)
        buf.put(deltaX)
        sendBytes(buf.array())
    }

    fun sendKeyTap(vkCode: Short) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTE)
        buf.put(TYPE_KEY_TAP)
        buf.putShort(vkCode)
        sendBytes(buf.array())
    }

    fun sendUnicodeChar(char: Char) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buf.put(MAGIC_BYTE)
        buf.put(TYPE_UNICODE_CHAR)
        buf.putChar(char)
        sendBytes(buf.array())
    }

    private fun startPingLoop() {
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                pingSeq++
                val now = System.currentTimeMillis()
                pingTimestamps[pingSeq] = now

                val buf = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
                buf.put(MAGIC_BYTE)
                buf.put(TYPE_PING)
                buf.putInt(pingSeq)
                sendBytes(buf.array())

                delay(1000)
            }
        }
    }

    private fun startReceiveLoop() {
        receiveJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(64)
            val packet = DatagramPacket(buf, buf.size)
            while (isActive) {
                try {
                    socket?.receive(packet)
                    if (packet.length >= 6 && buf[0] == MAGIC_BYTE && buf[1] == TYPE_PONG) {
                        val seq = ByteBuffer.wrap(buf, 2, 4).order(ByteOrder.BIG_ENDIAN).int
                        val sentTime = pingTimestamps.remove(seq)
                        if (sentTime != null) {
                            val rtt = (System.currentTimeMillis() - sentTime).toInt()
                            _latencyMs.value = rtt
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
