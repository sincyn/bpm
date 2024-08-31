package noderspace.common.packets.internal

import noderspace.common.logging.KotlinLogging
import noderspace.common.memory.Buffer
import noderspace.common.network.NetUtils
import noderspace.common.packets.Packet
import java.util.UUID

data class ConnectRequest(var uuid: UUID = NetUtils.DefaultUUID) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        if (NetUtils.isDefaultUUID(uuid)) logger.warn { "UUID is null before write" }
        buffer.writeUUID(uuid)
    }
    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        uuid = buffer.readUUID() //This could throw an exception if the uuid isn't set when serializing
        if (NetUtils.isDefaultUUID(uuid)) logger.warn { "UUID is null after read" }
    }

    companion object {

        private val logger = KotlinLogging.logger {}
    }
}