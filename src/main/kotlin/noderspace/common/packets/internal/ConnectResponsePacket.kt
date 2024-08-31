package noderspace.common.packets.internal

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet

data class ConnectResponsePacket(var valid: Boolean = false) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        buffer.writeBoolean(valid)
    }
    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        valid = buffer.readBoolean()
    }

}