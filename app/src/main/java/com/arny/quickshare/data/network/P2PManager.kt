package com.arny.quickshare.data.network

import android.content.Context
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.origin
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class P2PManager(private val context: Context) {

    private var server: ApplicationEngine? = null
    private var currentSession: WebSocketSession? = null

    private val _status = MutableStateFlow("Не подключено")
    val status: StateFlow<String> = _status

    private val _receivedText = MutableStateFlow("")
    val receivedText: StateFlow<String> = _receivedText

    private val _history = mutableListOf<String>().apply { add("История:") }
    val history: List<String> get() = _history.toList()

    private val client = HttpClient(CIO) {
        install(WebSockets)
        install(ContentNegotiation) {
            json()
        }
    }

    init {
        checkPermissions()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.INTERNET)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            _status.value = "Ошибка: Нет разрешения на интернет"
        }
    }

    fun startServer(port: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(CIO, port) {
                    install(WebSockets)
                    install(Compression)
                    install(ContentNegotiation) {
                        json()
                    }
                    routing {
                        webSocket("/p2p") {
                            handleWebSocketConnection(this)
                        }
                    }
                }.apply {
                    start(wait = false)
                    _status.value = "Сервер запущен на порту $port (${getLocalIpAddress()}:$port)"
                }
            } catch (e: Exception) {
                _status.value = "Ошибка запуска сервера: ${e.message}"
            }
        }
    }

    suspend fun connectToServer(ip: String, port: Int) {
        try {
            client.webSocket(
                method = HttpMethod.Get,
                host = ip,
                port = port,
                path = "/p2p"
            ) {
                currentSession = this
                _status.value = "Подключено к $ip:$port"
                listenForMessages()
            }
        } catch (e: Exception) {
            _status.value = "Ошибка подключения: ${e.message}"
        }
    }

    private suspend fun handleWebSocketConnection(session: WebSocketServerSession) {
        currentSession = session
        _status.value = "Новое соединение: ${session.call.request.origin.remoteHost}"
        listenForMessages()
    }

    private suspend fun WebSocketSession.listenForMessages() {
        try {
            for (message in incoming) {
                message as? Frame.Text ?: continue
                val (time, text) = parseMessage(message.readText())
                CoroutineScope(Dispatchers.Main).launch {
                    _receivedText.value = text
                    addToHistory("Получено в $time: $text")
                }
            }
        } catch (e: Exception) {
            _status.value = "Ошибка соединения: ${e.message}"
        } finally {
            close()
            _status.value = "Соединение закрыто"
        }
    }

    suspend fun sendMessage(text: String) {
        currentSession?.let { session ->
            if (session.isActive) {
                val time = fetchServerTime()
                val message = "time=$time;text=$text"
                try {
                    session.send(message)
                    addToHistory("Отправлено в $time: $text")
                } catch (e: Exception) {
                    _status.value = "Ошибка отправки: ${e.message}"
                }
            }
        } ?: run {
            _status.value = "Ошибка: Нет активного соединения"
        }
    }

    private suspend fun fetchServerTime(): String {
        return try {
            client.get("https://timeapi.io/api/Time/current/zone?timeZone=UTC") {
                contentType(ContentType.Application.Json)
            }.body<TimeResponse>().dateTime
        } catch (e: Exception) {
            LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        }
    }

    private fun parseMessage(message: String): Pair<String, String> {
        val parts = message.split(";")
        return parts[0].substringAfter("time=") to parts[1].substringAfter("text=")
    }

    private fun addToHistory(entry: String) {
        if (_history.size > 10) _history.removeAt(1)
        _history.add(entry)
    }

    private fun getLocalIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return android.text.format.Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    }

    fun stopServer() {
        server?.stop(500, 1000)
        server = null
        _status.value = "Сервер остановлен"
    }

    fun dispose() {
        client.close()
        server?.stop(500, 1000)
    }

    data class TimeResponse(val dateTime: String)
}