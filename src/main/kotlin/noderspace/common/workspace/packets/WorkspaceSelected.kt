package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.network.NetUtils
import noderspace.common.packets.Packet
import java.util.UUID

/**
 * Represents a packet for selecting a workspace.
 *
 * @property workspaceUid The uuid of the selected workspace.
 */
data class WorkspaceSelected(var workspaceUid: UUID = NetUtils.DefaultUUID) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        buffer.writeUUID(workspaceUid)
    }

    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        workspaceUid = buffer.readUUID()
    }

    override fun toString(): String {
        return "WorkspaceSelected('$workspaceUid')"
    }
}