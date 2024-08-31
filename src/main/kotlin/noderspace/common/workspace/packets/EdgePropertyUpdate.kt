package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.network.NetUtils
import noderspace.common.packets.Packet
import noderspace.common.property.Property
import noderspace.common.property.PropertyMap
import noderspace.common.serial.Serial
import java.util.UUID

class EdgePropertyUpdate(var edgeUid: UUID = NetUtils.DefaultUUID, var property: PropertyMap = Property.Object()) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        buffer.writeUUID(edgeUid)
        Serial.write(buffer, property)
    }


    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        edgeUid = buffer.readUUID()
        property = Serial.read(buffer) ?: throw IllegalStateException("Property is null")
    }
}