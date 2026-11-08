package com.neil1454.drawsomething.net

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

sealed class TransportEvent {
    data class HostFound(val id: String, val name: String) : TransportEvent()
    data class HostLost(val id: String) : TransportEvent()

    /**
     * [incoming] = someone else asked to connect to us (we are the host).
     * [code] is a 4-digit pairing code derived from the encrypted handshake: both screens show
     * the same digits, and the players check they match. A stranger (or a man in the middle)
     * can't produce a matching code.
     */
    data class ConnectionRequest(val id: String, val name: String, val incoming: Boolean, val code: String) : TransportEvent()
    data class Connected(val id: String, val name: String) : TransportEvent()

    /** A pending or attempted connection ended without connecting (declined, cancelled, failed). */
    data class ConnectionFailed(val id: String) : TransportEvent()
    data class Disconnected(val id: String) : TransportEvent()
    data class Failure(val message: String) : TransportEvent()
    class Received(val bytes: ByteArray) : TransportEvent()
}

/**
 * Phone-to-phone link using Google's Nearby Connections API.
 *
 * Nearby picks the best radio on its own (Bluetooth, BLE, Wi-Fi Direct / hotspot / LAN) so
 * it works with NO internet: two phones on a plane just need Bluetooth and/or Wi-Fi switched
 * on. All callbacks arrive on the main thread.
 *
 * Security: the link is encrypted and authenticated by Nearby; connections need a manual
 * accept from BOTH sides after comparing the pairing code; once one partner is connected we stop
 * advertising/searching and refuse every other connection; and data is only ever read from
 * the one paired endpoint.
 */
class NearbyTransport(
    context: Context,
    private val onEvent: (TransportEvent) -> Unit,
) {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val names = HashMap<String, String>()
    private val pending = HashSet<String>()
    private var myName = ""
    private var peerId: String? = null

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // Only the one paired device is ever listened to.
            if (endpointId != peerId) return
            payload.asBytes()?.let { onEvent(TransportEvent.Received(it)) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            if (peerId != null) {
                // Already paired: nobody else gets in.
                client.rejectConnection(endpointId)
                return
            }
            names[endpointId] = info.endpointName
            pending += endpointId
            onEvent(
                TransportEvent.ConnectionRequest(
                    endpointId, info.endpointName, info.isIncomingConnection, info.authenticationDigits,
                ),
            )
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            pending -= endpointId
            if (result.status.isSuccess && peerId == null) {
                peerId = endpointId
                // Lock the door: stop being visible and refuse everything else still pending.
                client.stopAdvertising()
                client.stopDiscovery()
                pending.toList().forEach { client.rejectConnection(it) }
                pending.clear()
                onEvent(TransportEvent.Connected(endpointId, names[endpointId] ?: "Partner"))
            } else {
                if (result.status.isSuccess) client.disconnectFromEndpoint(endpointId)
                onEvent(TransportEvent.ConnectionFailed(endpointId))
            }
        }

        override fun onDisconnected(endpointId: String) {
            pending -= endpointId
            if (endpointId == peerId) {
                peerId = null
                onEvent(TransportEvent.Disconnected(endpointId))
            }
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            onEvent(TransportEvent.HostFound(endpointId, info.endpointName))
        }

        override fun onEndpointLost(endpointId: String) {
            onEvent(TransportEvent.HostLost(endpointId))
        }
    }

    /** Host: become visible to nearby players under [name]. */
    fun startHosting(name: String) {
        myName = name
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(name, SERVICE_ID, lifecycle, options)
            .addOnFailureListener { fail("Couldn't start hosting", it) }
    }

    /** Joiner: look for nearby hosts. */
    fun startDiscovery(name: String) {
        myName = name
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, discovery, options)
            .addOnFailureListener { fail("Couldn't search for games", it) }
    }

    fun connect(hostId: String) {
        client.requestConnection(myName, hostId, lifecycle)
            .addOnFailureListener { fail("Couldn't reach that game", it) }
    }

    fun accept(id: String) {
        client.acceptConnection(id, payloads).addOnFailureListener { fail("Couldn't accept", it) }
    }

    fun reject(id: String) {
        client.rejectConnection(id)
    }

    /** Stop advertising/searching once we're connected (the link stays up). */
    fun stopLooking() {
        client.stopAdvertising()
        client.stopDiscovery()
    }

    fun send(bytes: ByteArray) {
        val id = peerId ?: return
        client.sendPayload(id, Payload.fromBytes(bytes))
    }

    /** Drop everything. */
    fun stopAll() {
        peerId = null
        names.clear()
        pending.clear()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    private fun fail(what: String, e: Exception) {
        onEvent(TransportEvent.Failure("$what. Make sure Bluetooth or Wi-Fi is on (no internet needed). ${e.message.orEmpty()}".trim()))
    }

    private companion object {
        const val SERVICE_ID = "com.neil1454.drawsomething"
        val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
    }
}
