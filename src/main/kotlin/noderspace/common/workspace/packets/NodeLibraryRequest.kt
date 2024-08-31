package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet
import noderspace.common.property.PropertyList
import noderspace.common.serial.Serial
import noderspace.common.type.NodeLibrary

class NodeLibraryRequest(val nodes: String = "/") : Packet {

    override fun serialize(buffer: Buffer) {
        buffer.writeString(nodes)
    }

    override fun deserialize(buffer: Buffer) {
        buffer.readString()
    }
}