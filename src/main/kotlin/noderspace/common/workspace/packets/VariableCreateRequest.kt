package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet
import noderspace.common.property.Property
import noderspace.common.property.PropertyMap
import noderspace.common.serial.Serial

data class VariableCreateRequest(
    var name: String = "",
    var property: PropertyMap = Property.Object()
) : Packet {

    override fun serialize(buffer: Buffer) {
        buffer.writeString(name)
        Serial.write(buffer, property)
    }

    override fun deserialize(buffer: Buffer) {
        name = buffer.readString()
        property = Serial.read(buffer) ?: Property.Object()
    }

}