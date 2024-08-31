package noderspace.common.packets.graph

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet
import noderspace.common.property.Property
import noderspace.common.property.PropertyList
import noderspace.common.serial.Serial

/**
 * Represents a packet that is sent from the server to the client containing a list of graphs.
 */
class GraphListResponsePacket(var graphList: PropertyList = Property.List()) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        Serial.write(buffer, graphList)
    }
    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        graphList = Serial.read(buffer) ?: error("Failed to deserialize graph list")
    }


}