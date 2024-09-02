package noderspace.common.network

import noderspace.common.logging.KotlinLogging
import noderspace.common.network.Endpoint.Side
import noderspace.common.packets.Packet
import java.util.UUID

/**
 * An interface for listening to network events.
 */
interface Listener {

    /**
     * Represents a worker object.
     *
     * This variable provides access to the worker object obtained from an endpoint.
     */
    val worker: Worker get() = Endpoint.serverRef.get().worker

    /**
     * Fetches the client from the endpoint reference.
     *
     * @return The client fetched from the endpoint reference.
     * @throws IllegalStateException if the endpoint is not a client.
     */
    val client: Client
        get() {
            val endpoint = Endpoint.clientRef.get()
            if (endpoint !is Client) throw IllegalStateException("Endpoint is not a client")
            return endpoint
        }

    /**
     * Returns the server instance.
     *
     * @throws IllegalStateException if the endpoint is not a server.
     * @return the server instance.
     */
    val server: Server
        get() {
            val endpoint = Endpoint.serverRef.get()
            if (endpoint !is Server) throw IllegalStateException("Endpoint is not a server")
            return endpoint
        }

    /**
     * Represents the connection type, either server or client.
     *
     * The `side` variable returns the current connection side based on the endpoint type.
     * If the endpoint is a `Client`, the `side` will be `NetSide.CLIENT`.
     * If the endpoint is a `Server`, the `side` will be `NetSide.SERVER`.
     *
     * @throws IllegalStateException if the endpoint is neither a `Client` nor a `Server`.
     *
     * @see Side
     * @see Client
     * @see Server
     */
//    val side: Side
//        get() {
//            val endpoint = Endpoint.serverRef.get()
//            return when (endpoint) {
//                is Client -> Side.CLIENT
//                is Server -> Side.SERVER
//                else -> throw IllegalStateException("Endpoint is not a client or server")
//            }
//        }


    /**
     * Called when the listener is installed and the endpoint is started.
     */
    fun onInstall() = Unit

    /**
     * Called when the listener is uninstalled and the endpoint is stopped.
     */
    fun onUninstall() = Unit

    /**
     * Called when a packet is received.
     *
     * @param packet the packet that was received
     */
    fun onPacket(packet: Packet, from: UUID) = Unit

    /**
     * Executes the logic for each tick of the game loop.
     *
     * @param delta The elapsed time since the last tick, in milliseconds.
     * @return Nothing is explicitly returned.
     */
    fun onTick(delta: Float, tick: Int): Unit = Unit

    /**
     * This method is called when a connection with a unique identifier (UUID) is established.
     *
     * @param uuid The unique identifier (UUID) of the connected device.
     */
    fun onConnect(uuid: UUID) =
        if (NetUtils.isDefaultUUID(uuid)) logger.debug { "Connected to server" } else logger.debug { "User $uuid connected" }

    /**
     * Called when a user with the given UUID disconnects from the system.
     *
     * @param uuid The UUID of the user who disconnected.
     */
    fun onDisconnect(uuid: UUID) = logger.debug { "User $uuid disconnected" }

    companion object {

        private val logger = KotlinLogging.logger {}
    }
}

inline fun <reified T : Listener> listener(side: Side): T = Endpoint.get(side).installed<T>()