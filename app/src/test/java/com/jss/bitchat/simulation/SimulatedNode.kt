package com.jss.bitchat.simulation

import com.jss.bitchat.core.mesh.MeshEvent
import com.jss.bitchat.core.mesh.MeshRouter
import com.jss.bitchat.core.mesh.PeerId
import com.jss.bitchat.core.protocol.PacketCodec
import com.jss.bitchat.core.protocol.PacketValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Encapsulates a simulated node consisting of its [PeerId], [SimulatedPacketTransport],
 * and production [MeshRouter].
 */
class SimulatedNode(
    val peerId: PeerId,
    val transport: SimulatedPacketTransport,
    val router: MeshRouter,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
) {
    val recordedMeshEvents = mutableListOf<MeshEvent>()
    private var eventJob: Job? = null

    fun start() {
        router.start()
        eventJob = scope.launch {
            router.events.collect {
                synchronized(recordedMeshEvents) {
                    recordedMeshEvents.add(it)
                }
            }
        }
    }

    fun stop() {
        router.stop()
        eventJob?.cancel()
    }

    fun clearEvents() {
        synchronized(recordedMeshEvents) {
            recordedMeshEvents.clear()
        }
    }
}

