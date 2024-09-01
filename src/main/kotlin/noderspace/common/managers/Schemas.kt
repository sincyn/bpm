package noderspace.common.managers

import noderspace.common.logging.KotlinLogging
import noderspace.common.network.Endpoint
import noderspace.common.network.Listener
import noderspace.common.packets.Packet
import noderspace.common.type.NodeLibrary
import noderspace.common.workspace.packets.NodeLibraryReloadRequest
import noderspace.common.workspace.packets.NodeLibraryRequest
import noderspace.common.workspace.packets.NodeLibraryResponse
import java.nio.file.Path
import java.util.*

class Schemas(private val path: Path) : Listener {

    val library: NodeLibrary = NodeLibrary()
    private val logger = KotlinLogging.logger { }
    //    private val path = Path.of("C:\\Users\\jraynor\\Documents\\nodes\\graph-common\\src\\main\\resources\\assets\\schemas")
//    private val path = Path.of("/home/randy/nodeer/schemas")


    override fun onInstall() {
        if (side == Endpoint.Side.CLIENT) return
        //Load the node library, todo: move this to a config file
        library.readFrom(path)
        val types = library.collect()
        logger.info { "Loaded ${types.size} types" }
    }

    override fun onConnect(uuid: UUID) {
        if (side == Endpoint.Side.SERVER) return
        //Request the node library from the server
        client.send(NodeLibraryRequest())
    }

    override fun onPacket(packet: Packet, from: UUID) {
        if (packet is NodeLibraryRequest) {
            //Send the node library to the client
            val response = NodeLibraryResponse(library.collectToPropertyList())
            //Send the response to the client
            server.send(response, from)
            logger.debug { "Sent node library to client $from with ${library.count()} types" }
        }
        if (packet is NodeLibraryResponse) {
            if (side == Endpoint.Side.CLIENT) {
                library.clear()
                library.loadFrom(packet.nodeSchemas)
                logger.debug { "Received node library from server with ${library.count()} types" }
            }
        }
        if (packet is NodeLibraryReloadRequest) {
            library.clear()
            library.readFrom(path)
            val types = library.collect()
            logger.info { "Loaded ${types.size} types" }
            //send to all in workspace
            server.sendToAll(NodeLibraryResponse(library.collectToPropertyList()))
        }
    }

}