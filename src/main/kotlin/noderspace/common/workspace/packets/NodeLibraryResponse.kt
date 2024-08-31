package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet
import noderspace.common.property.PropertyList
import noderspace.common.serial.Serial
import noderspace.common.type.NodeLibrary

data class NodeLibraryResponse(var nodeSchemas: PropertyList = NodeLibrary().collectToPropertyList()) : Packet {

    override fun serialize(buffer: Buffer) {
        Serial.write(buffer, nodeSchemas)
    }

    override fun deserialize(buffer: Buffer) {
        nodeSchemas = Serial.read(buffer) ?: error("Failed to deserialize node library")
    }
}