package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet
import noderspace.common.property.PropertyMap
import noderspace.common.serial.Serial
import noderspace.common.workspace.graph.Node
import org.joml.Vector2f

data class NodeCreated(var node: Node = Node()) : Packet {

    /**
     * Serializes the provided Buffer.
     *
     * @param buffer The Buffer to be serialized.
     */
    override fun serialize(buffer: Buffer) {
        Serial.write(buffer, node.properties)
    }


    /**
     * Deserializes the given buffer.
     *
     * @param buffer The buffer to deserialize.
     */
    override fun deserialize(buffer: Buffer) {
        node.properties.clear()
        val properties: PropertyMap = Serial.read(buffer) ?: error("Failed to read properties from buffer!")
        node.properties.putAll(properties)
    }
}