package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet

data class WorkspaceLibraryRequest(val directory: String = "/") : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        buffer.writeString(directory)
    }
    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        buffer.readString()
    }

}