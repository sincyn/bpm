package noderspace.common.network


import bpm.network.MinecraftNetworkAdapter
import bpm.network.ServerTarget
import net.minecraft.client.Minecraft
import noderspace.common.logging.KotlinLogging
import noderspace.common.network.Network.new
import noderspace.common.packets.*
import noderspace.common.packets.internal.*
import java.net.SocketException
import java.util.*

class Client() : Endpoint<Client>() {

    // Gets the current player's UUID
    private val uuid by lazy { Minecraft.getInstance().player?.uuid ?: error("Player UUID not available") }

    init {
        clientRef.set(this)
    }


    private val logger = KotlinLogging.logger {}

    /**
     * Represents a worker object.
     *
     * @property worker The instance of the Worker class.
     */
    override val worker: Worker = Worker(this)


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

    fun connect(): Client {
        send(new<ConnectRequest> {
            this.uuid = this@Client.uuid
        })
        connected = true
        return this
    }


    /**
     * Initiates the connection to the server.
     */
    override fun initiate() {
        try {
            worker.start()
        } catch (e: Exception) {
            logger.error {
                "Failed to start client thread"
            }
            runningRef.set(false)
        }
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

        } catch (e: Exception) {
            //I don't give a fuck xD
        }
    }


    /**
     * Sends a packet to the server.
     */
    override fun send(packet: Packet, id: Connection?) {
        try {
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
     * Retrieves the current connection.
     */
    override fun get(connectionID: UUID): Connection? {
        return null
    }

    companion object {

        internal inline operator fun <reified Result : Any?> invoke(noinline optBlk: (Client.() -> Result)? = null): Client {
            val client = if (clientRef.get() == null) {
                //Create the server, which initializes the serverRef to this server instance
                Client()
            } else clientRef.get() as Client
            if (optBlk != null) client.optBlk()
            return client
        }
    }
}

