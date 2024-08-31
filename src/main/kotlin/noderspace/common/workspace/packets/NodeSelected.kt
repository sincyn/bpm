package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.network.NetUtils
import noderspace.common.packets.Packet
import java.util.UUID

data class NodeSelected(var nodeId: UUID = NetUtils.DefaultUUID) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        buffer.writeUUID(nodeId)
    }

    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        nodeId = buffer.readUUID()
    }
}