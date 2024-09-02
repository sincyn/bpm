package noderspace.common.network


import bpm.network.MinecraftNetworkAdapter
import bpm.network.ServerTarget
import noderspace.common.logging.KotlinLogging
import noderspace.common.network.Network.new
import noderspace.common.packets.*
import noderspace.common.packets.internal.*
import java.net.Socket
import java.net.SocketException
import java.util.*

class Client(val uuid: UUID) :
    Endpoint<Client>(),
    Runnable {

    init {
        clientRef.set(this)
    }

    private var ipAddress: String = "localhost"
    private var port: Int = 33456

    private val logger = KotlinLogging.logger {}
    /**
     * Represents a private lateinit variable that holds a Socket instance.
     * The Socket class provides the ability to establish a socket connection with a server.
     */
    private lateinit var socket: Socket

    /**
     * A private variable representing a Thread instance.
     *
     * This variable is used to create and manage a new thread for network client operations.
     *
     * @property thread The Thread instance associated with the network client.
     */
    private val thread: Thread = Thread(this, "Network Client")

    /**
     * Represents a worker object.
     *
     * @property worker The instance of the Worker class.
     */
    override val worker: Worker = Worker(this)

    /**
     * Gets the connection.
     *
     * @return The connection object.
     */
    private var connection: Connection? = null
    /**
     * Flag indicating whether the variable is terminated or not.
     */
    var terminated = false
        private set

    /**
     * Indicates whether a connection is established or not.
     *
     * @property connected True if a connection is established, false otherwise.
     */
    var connected = false
        private set

    override fun connected(id: Connection) {
        send(new<ConnectRequest> {
            this.uuid = this@Client.uuid
        })
    }


    fun connect(ipAddress: String, port: Int): Client {
        try {

            this.ipAddress = ipAddress
            this.port = port
//            socket = Socket(ipAddress, port)
//            connection = Connection(uuid, Side.CLIENT, socket)
            connected = true
            send(new<ConnectRequest> {
                this.uuid = this@Client.uuid
            })
        } catch (e: Exception) {
            logger.error {
                "Failed to connect to server at $ipAddress:$port"
            }
            runningRef.set(false)
        }
        return this
    }

    /**
     * Initiates the connection to the server.
     */
    override fun initiate() {
        try {
//            thread.isDaemon = true
//            thread.start()
            worker.start()
        } catch (e: Exception) {
            logger.error {
                "Failed to start client thread"
            }
            runningRef.set(false)
        }
    }

    /**
     * Handles the client's operations in a separate thread.
     */
    override fun run() {
//        while (runningRef.get()) {
//            if (!connected) continue
//
////            val packet = receive(connection) ?: continue
////            when (packet) {
////                is ConnectResponsePacket -> {
////                    if (!packet.valid) {
////                        logger.error { "Connection was not valid" }
//////                        disconnected(connection)
////                        terminate()
////                        return
////                    }
////                    synchronized(listeners) {
////                        listeners.forEach {
////                            it.onConnect(NetUtils.DefaultUUID)
////                        }
////                    }
////                }
////
////                is DisconnectPacket -> {
////                    if (packet.uuid != uuid) {
////                        logger.warn { "Received disconnect packet with invalid UUID" }
////                        return
////                    }
////                    disconnect()
////                    return
////                }
////
////                else -> worker.queue(packet, NetUtils.DefaultUUID)
////
////            }
//
//        }
//        terminate()
    }

    internal fun disconnect() {
        // notify the server that we're disconnecting
        send(new<DisconnectPacket> {
            this.uuid = this@Client.uuid
        })
        logger.info { "Sent client disconnection packet" }
        synchronized(listeners) {
            listeners.forEach {
                it.onDisconnect(NetUtils.DefaultUUID)
            }
        }

        connected = false
    }

    /**
     * Notifies the server that the client is disconnecting and triggers the disconnection process.
     *
     * @param id The ID of the connection to disconnect.
     */
    override fun disconnected(id: Connection) {
        // notify the server that we're disconnecting
        send(new<DisconnectPacket> {
            this.uuid = this@Client.uuid
        }, id)
        logger.info { "Sent client disconnection packet" }
        synchronized(listeners) {
            listeners.forEach {
                it.onDisconnect(id.uuid)
            }
        }
    }
    /**
     * Terminates the client connection.
     */
    override fun terminate() {
        runningRef.set(false)
        try {
//            disconnected(connection)

            // notify the server that we're disconnecting
            send(new<DisconnectPacket> {
                this.uuid = this@Client.uuid
            })
            logger.info { "Sent client disconnection packet" }
            synchronized(listeners) {
                listeners.forEach {
                    it.onDisconnect(uuid)
                }
            }

//            socket.close()
        } catch (e: Exception) {
            logger.error(e) { "Failed to close socket ${e.message}" }
        }
    }


    /**
     * Sends a packet to the server.
     */
    override fun send(packet: Packet, id: Connection?) {
        try {
//            val input = socket.getOutputStream() ?: throw SocketException("Socket output stream is null")
//            packet.write(input)
            MinecraftNetworkAdapter.sendPacket(packet, ServerTarget)
            logger.info { "Sent packet of type ${packet::class.simpleName} with id ${packet.id}" }
        } catch (ex: SocketException) {
            runningRef.set(false)
            logger.warn { "Socket exception: ${ex.message}" }
            connected = false
        }
    }
    /**
     * Sends the given packet to all connected endpoints.
     */
    override fun sendToAll(packet: Packet, vararg exclude: UUID) = send(packet)

    /**
     * Receives a packet from the specified connection.
     *
     * @param id The connection from which to receive the packet. Default value is null.
     * @return The received packet, or null if no packet is received.
     */
    override fun receive(id: Connection?): Packet? {
//        if (!runningRef.get()) return null
//        try {
//            val input = id?.socket?.getInputStream() ?: throw SocketException("Socket is null")
//            val packet = input.readPacket() ?: return null
//            logger.info { "Received packet of type ${packet::class.simpleName} with id ${packet.id}" }
//            return packet
//        } catch (ex: SocketException) {
//            logger.warn { "Socket exception: ${ex.message}" }
//            runningRef.set(false)
//            return null
//        }
        return null
    }

    /**
     * Retrieves the current connection.
     */
    override fun get(connectionID: UUID): Connection? {
        return null
    }

    companion object {

        operator fun invoke(): Client {
            return Endpoint.clientRef.get() as Client
        }
    }

    // Implement other methods if needed
}
