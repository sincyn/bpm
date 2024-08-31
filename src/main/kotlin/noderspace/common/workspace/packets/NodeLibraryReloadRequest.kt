package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet

class NodeLibraryReloadRequest(val nodes: String = "/") : Packet {

    override fun serialize(buffer: Buffer) {
        buffer.writeString(nodes)
    }

    override fun deserialize(buffer: Buffer) {
        buffer.readString()
    }
}