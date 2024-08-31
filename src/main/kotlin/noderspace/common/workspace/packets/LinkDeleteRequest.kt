package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.network.NetUtils
import noderspace.common.packets.Packet
import noderspace.common.property.PropertyMap
import noderspace.common.serial.Serial
import noderspace.common.workspace.graph.Node
import org.joml.Vector2f
import java.util.*

data class LinkDeleteRequest(var uuid: UUID = NetUtils.DefaultUUID) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        buffer.writeUUID(uuid)
    }


    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        uuid = buffer.readUUID() ?: error("Failed to read properties from buffer!")
    }
}