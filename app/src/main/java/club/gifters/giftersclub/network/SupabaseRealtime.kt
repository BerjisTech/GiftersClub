package club.gifters.giftersclub.network

import android.content.Context
import android.util.Log
import club.gifters.giftersclub.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseRealtime(private val context: Context) {
    private var socket: WebSocket? = null
    private var joined: Boolean = false
    private var refCounter = 1
    private var callbacks: MutableList<(Int) -> Unit> = mutableListOf()

    fun subscribeToLiveTaps(streamId: String, onUpdate: (Int) -> Unit) {
        callbacks += onUpdate
        if (socket != null) return
        val wsUrl = SupabaseConfig.SUPABASE_URL.replaceFirst("http", "ws") + "/realtime/v1/websocket"
        val urlBuilder = wsUrl.toHttpUrlOrNull()?.newBuilder()
            ?: return
        urlBuilder.addQueryParameter("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
        urlBuilder.addQueryParameter("vsn", "1.0.0")
        // supply Authorization via query param per Realtime server convention
        val jwt = context.getSharedPreferences("supabase", Context.MODE_PRIVATE).getString("access_token", null)
        if (!jwt.isNullOrEmpty()) {
            urlBuilder.addQueryParameter("authorization", "Bearer%20$jwt")
        }
        val url = urlBuilder.build().toString()
        val req = Request.Builder().url(url).build()
        val client = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                try {
                    val cfg = JSONObject().apply {
                        put("broadcast", JSONObject().put("ack", false).put("self", false))
                        put("presence", JSONObject().put("key", "user_id"))
                        put(
                            "postgres_changes",
                            JSONArray().put(
                                JSONObject()
                                    .put("event", "UPDATE")
                                    .put("schema", "public")
                                    .put("table", "live_streams")
                                    .put("filter", "id=eq.$streamId")
                            )
                        )
                    }
                    val join = JSONObject().apply {
                        put("topic", "realtime:public:live_streams")
                        put("event", "phx_join")
                        put("payload", JSONObject().put("config", cfg))
                        put("ref", (refCounter++).toString())
                    }
                    webSocket.send(join.toString())
                } catch (e: Exception) {
                    Log.e("SupabaseRealtime", "join failed", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val obj = JSONObject(text)
                    val event = obj.optString("event")
                    if (event == "postgres_changes") {
                        val payload = obj.optJSONObject("payload")
                        val rec = payload?.optJSONObject("record")
                        val taps = rec?.optInt("taps", -1) ?: -1
                        if (taps >= 0) {
                            CoroutineScope(Dispatchers.Main).launch {
                                callbacks.forEach { it.invoke(taps) }
                            }
                        }
                    }
                } catch (_: Exception) { }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                joined = false
                socket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SupabaseRealtime", "failure", t)
                joined = false
                socket = null
            }
        })
    }

    fun close() {
        try { socket?.close(1000, "bye") } catch (_: Exception) {}
        socket = null
        joined = false
        callbacks.clear()
    }
}
