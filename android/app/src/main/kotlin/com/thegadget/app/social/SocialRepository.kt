package com.thegadget.app.social

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.thegadget.app.core.Clock
import com.thegadget.app.core.Ids
import com.thegadget.app.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The homies transport, ported from `src/utils/social.ts`. Two public brokers over secure
 * WebSockets, the `gadget/v1` topic tree, QoS-1 retained presence with an MQTT last-will
 * tombstone, and QoS-1 non-retained peer-to-peer chat relayed by the broker. No account, no
 * server of our own — identical protocol to the web client.
 */
@Serializable
data class Presence(
    val v: Int = 1,
    val t: Long = 0,
    val up: Boolean? = null,
    val name: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    val avatar: String? = null,
    val listenedSec: Long? = null,
    val games: Int? = null,
)

@Serializable
data class ChatMsg(
    val id: String,
    val from: String,
    val name: String,
    val text: String,
    val t: Long,
)

object Social {
    private const val ROOT = "gadget/v1"
    private val BROKERS = listOf(
        "broker.hivemq.com" to 8883,
        "broker.emqx.io" to 8883,
    )
    private val json = Json { ignoreUnknownKeys = true }

    private var client: Mqtt5AsyncClient? = null
    private var brokerIdx = 0
    private var myCode: String = ""
    private val watching = mutableSetOf<String>()

    private val _presence = MutableStateFlow<Map<String, Presence>>(emptyMap())
    val presence: StateFlow<Map<String, Presence>> = _presence.asStateFlow()

    private val _chat = MutableStateFlow<Map<String, List<ChatMsg>>>(emptyMap())
    val chat: StateFlow<Map<String, List<ChatMsg>>> = _chat.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private fun pTopic(c: String) = "$ROOT/p/$c"
    private fun chatTopic(a: String, b: String) = "$ROOT/c/" + if (a < b) "$a/$b" else "$b/$a"

    private fun clientId() = "hpw" + (1..10).map { "abcdefghijklmnopqrstuvwxyz0123456789"[Random.nextInt(36)] }.joinToString("")

    fun connect(myCode: String, codes: List<String>) {
        this.myCode = myCode
        watching.clear(); watching += codes.filter { it.isNotEmpty() && it != myCode }
        val (host, port) = BROKERS[brokerIdx % BROKERS.size]
        val tombstone = json.encodeToString(Presence.serializer(), Presence(t = Clock.now(), up = false))
        val will = com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5WillPublish.builder()
            .topic(pTopic(myCode))
            .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
            .payload(tombstone.toByteArray())
            .retain(true)
            .build()
        val built = MqttClient.builder()
            .useMqttVersion5()
            .serverHost(host)
            .serverPort(port)
            .sslWithDefaultConfig()
            .identifier(clientId())
            .automaticReconnectWithDefaultConfig()
            .willPublish(will)
            .addConnectedListener {
                _connected.value = true
                publishPresence()
                resubscribe()
            }
            .addDisconnectedListener { _connected.value = false }
            .buildAsync()
        client = built
        built.connect().whenComplete { _: Any?, err: Throwable? ->
            if (err != null) {
                brokerIdx++
                Log.w("social", "broker connect failed", err)
            }
        }
    }

    private fun resubscribe() {
        val c = client ?: return
        val topics = watching.flatMap { listOf(pTopic(it), chatTopic(myCode, it)) }
        topics.forEach { t ->
            c.subscribeWith().topicFilter(t).qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                .callback { p -> onMessage(p) }
                .send()
        }
    }

    private fun onMessage(p: Mqtt5Publish) {
        val topic = p.topic.toString()
        val body = p.payloadAsBytes?.toString(Charsets.UTF_8) ?: return
        when {
            topic.startsWith("$ROOT/p/") -> {
                val code = topic.substringAfterLast('/')
                val pres = runCatching { json.decodeFromString(Presence.serializer(), body) }.getOrNull() ?: return
                _presence.value = _presence.value + (code to pres)
            }
            topic.startsWith("$ROOT/c/") -> {
                val msg = runCatching { json.decodeFromString(ChatMsg.serializer(), body) }.getOrNull() ?: return
                val peer = if (msg.from == myCode) topicPeer(topic) else msg.from
                val list = (_chat.value[peer] ?: emptyList()) + msg
                _chat.value = _chat.value + (peer to if (list.size > 200) list.takeLast(200) else list)
            }
        }
    }

    private fun topicPeer(topic: String): String {
        val parts = topic.removePrefix("$ROOT/c/").split("/")
        return parts.firstOrNull { it != myCode } ?: myCode
    }

    fun publishPresence(profile: Profile? = null) {
        val c = client ?: return
        val pres = Presence(
            t = Clock.now(), up = true, name = profile?.name, tagline = profile?.tagline, status = profile?.status,
        )
        c.publishWith().topic(pTopic(myCode)).qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
            .retain(true).payload(json.encodeToString(Presence.serializer(), pres).toByteArray()).send()
    }

    fun send(peer: String, text: String) {
        val c = client ?: return
        val msg = ChatMsg(
            id = "${Clock.now()}-${(1..5).map { "abcdefghijklmnopqrstuvwxyz0123456789"[Random.nextInt(36)] }.joinToString("")}",
            from = myCode, name = "", text = text, t = Clock.now(),
        )
        // Echo locally (the web buffer shows your own message immediately).
        val list = (_chat.value[peer] ?: emptyList()) + msg
        _chat.value = _chat.value + (peer to list)
        c.publishWith().topic(chatTopic(myCode, peer)).qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
            .retain(false).payload(json.encodeToString(ChatMsg.serializer(), msg).toByteArray()).send()
    }

    fun disconnect() {
        runCatching { client?.disconnect() }
        client = null
        _connected.value = false
    }
}
