package com.jss.bitchat.simulation

import com.jss.bitchat.core.mesh.PeerId

/**
 * Policy for packet delivery across a [SimulatedLink].
 */
fun interface PacketDeliveryPolicy {
    fun shouldDeliver(from: PeerId, to: PeerId, attemptNumber: Int): Boolean
}

/**
 * Models a link between two nodes with partition control and loss policies.
 */
class SimulatedLink(
    val nodeA: PeerId,
    val nodeB: PeerId,
    var isUp: Boolean = true,
    var deliveryPolicy: PacketDeliveryPolicy = PacketDeliveryPolicy { _, _, _ -> true }
) {
    private var transmissionCountAtoB = 0
    private var transmissionCountBtoA = 0

    fun shouldDeliver(from: PeerId, to: PeerId): Boolean {
        if (!isUp) return false
        val count = if (from == nodeA) ++transmissionCountAtoB else ++transmissionCountBtoA
        return deliveryPolicy.shouldDeliver(from, to, count)
    }

    fun resetCounters() {
        transmissionCountAtoB = 0
        transmissionCountBtoA = 0
    }
}

