package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.network.NetUtils
import noderspace.common.packets.Packet
import java.util.*

data class WorkspaceCompileRequest(var workspaceId: UUID = NetUtils.DefaultUUID) : Packet {

    override fun serialize(buffer: Buffer) {
        buffer.writeUUID(workspaceId)
    }

    override fun deserialize(buffer: Buffer) {
        workspaceId = buffer.readUUID()
    }

}