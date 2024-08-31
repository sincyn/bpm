package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet

class VariableDeleted(var name: String = "") : Packet {

    override fun serialize(buffer: Buffer) {
        buffer.writeString(name)
    }

    override fun deserialize(buffer: Buffer) {
        name = buffer.readString()
    }

}
