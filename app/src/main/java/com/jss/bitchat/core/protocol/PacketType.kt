package com.jss.bitchat.core.protocol

/**
 * Enum representing the type of a packet on the wire.
 * The [wireValue] must match the specification defined in
 * `docs/networking/engine-guide/03-packet-protocol.md`.
 */
enum class PacketType(val wireValue: Int) {
    DATA(1),
    ACK(2),
    CONTROL(3);

    companion object {
        private val map = values().associateBy { it.wireValue }
        fun fromWireValue(value: Int): PacketType? = map[value]
    }
}

