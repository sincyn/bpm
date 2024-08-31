package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet
import noderspace.common.property.Property
import noderspace.common.property.PropertyMap
import noderspace.common.serial.Serial
import org.joml.Vector2f


data class VariableUpdated(
    var variableName: String = "",
    var property: PropertyMap = Property.Object()
) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        buffer.writeString(variableName)
        Serial.write(buffer, property)
    }


    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        variableName = buffer.readString()
        property = Serial.read(buffer) ?: Property.Object()
    }
}