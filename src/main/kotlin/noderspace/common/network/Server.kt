package noderspace.common.network

import bpm.network.AllPlayersTarget
import bpm.network.MinecraftNetworkAdapter
import bpm.network.PacketTarget
import bpm.network.PlayerTarget
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.server.ServerLifecycleHooks
import noderspace.common.logging.KotlinLogging
import noderspace.common.network.Network.new
import noderspace.common.packets.*
import noderspace.common.packets.internal.*
import java.net.ServerSocket
import java.net.SocketException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean

class Server() : Endpoint<Server>() {


    init {
        serverRef.set(this)
    }

    // Create our logger
    private val logger = KotlinLogging.logger {}

    // Stores the connections to the server
    private val clients: ConcurrentMap<UUID, Endpoint<ClientWorker>> = ConcurrentHashMap()
    private val cachedClientPlayers: ConcurrentMap<UUID, PlayerTarget> = ConcurrentHashMap()
    override val worker = Worker(this)
    private val server: MinecraftServer by lazy {
        ServerLifecycleHooks.getCurrentServer() ?: error("Server not available")
    }

    /**
     * The executor used to handle client connections.
     */
    private val clientExecutors = ThreadPoolExecutor(
        6,
        20,
        Heartbeat.HEARTBEAT_TIMEOUT_SECONDS * 1000L,
        java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue(20),
    )


    /**
     * This should start the server or connect to the server depending on the implementation.
     */
    override fun initiate() {
        try {
            worker.start()
        } catch (e: Exception) {
            logger.error(e) { "Failed to start server" }
            return
        }
        //TODO: relay to listeners
    }

    /**
     * This should stop the server or disconnect from the server depending on the implementation.
     */
    override fun terminate() {
        runningRef.set(false)
        clients.forEach { (_, worker) -> worker.terminate() }
//        socket?.close()
        clientExecutors.shutdown()
        clients.clear()
        //TODO: relay to listeners
        logger.info { "Server terminated" }
    }


    /**
     * Called when a client has connected
     */
    override fun connected(id: Connection) {
//        logger.info { "Client ${id.uuid} connected" }
        clients.remove(id.uuid)
        send(new<ConnectResponsePacket> {
            this.valid = true
        }, id)
        id.uuid = id.uuid
        listeners.forEach {
            it.onConnect(id.uuid)
        }
        clients[id.uuid] = ClientWorker(id)
    }


    /**
     * Sends the given packet to a specified ID. If no ID is specified, the packet will be sent to all connected endpoints.
     * If the packet is not sent, this method will return 0. Otherwise, it will return the number of endpoints that the packet was sent to.
     *
     * @param packet The packet to send. This packet will be serialized and sent to the specified ID.
     * @param id The ID of the recipient (optional). If no ID is specified, the packet will be sent to all connected endpoints.
     */
    override fun send(packet: Packet, id: Connection?) {
        if (id == null) {
            clients.forEach { (_, worker) -> worker.send(packet) }
            return
        }
        clients[id.uuid]?.send(packet)
    }
    /**
     * Sends the given packet to all connected endpoints.
     */
    override fun sendToAll(packet: Packet, vararg exclude: UUID) {
        clients.forEach { (uuid, worker) ->
            if (exclude.contains(uuid)) return@forEach
            worker.send(packet)
        }
    }

    private fun targetOf(uuid: UUID): PacketTarget? {
        return cachedClientPlayers.computeIfAbsent(uuid) {
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                logger.warn { "Player $uuid not found" }
                return@computeIfAbsent null
            }
            PlayerTarget(player)
        }
    }

    /**
     * Retrieves the Connection associated with the specified connection ID.
     *
     * @param connectionID The ID of the connection to retrieve.
     * @return The Connection object associated with the given connection ID, or null if no such connection exists.
     */
    override fun get(connectionID: UUID): Connection? {
        return clients[connectionID]?.get(connectionID)
    }
    /**
     * Disconnects the specified user by their ID.
     *
     * @param id the ID of the user to be disconnected
     */
    override fun disconnected(id: Connection) {
        listeners.forEach {
            it.onDisconnect(id.uuid)
        }
        clients.remove(id.uuid)
        logger.warn { "Client ${id.uuid} disconnected" }
        //TODO: relay to listeners
    }

    /**
     * This class represents a ClientWorker, which is responsible for handling client connections in a server application.
     * It implements the Endpoint and Runnable interfaces.
     *
     * @property connection The Endpoint.Connection associated with this ClientWorker.
     * @property thread The Thread used to execute the run() method.
     * @property logger The logger used for logging.
     */
    private inner class ClientWorker(private val connection: Connection) : Endpoint<ClientWorker>() {

        private val running: AtomicBoolean = AtomicBoolean(false)
        override val worker get() = this@Server.worker

        private val logger = KotlinLogging.logger {}


        /**
         * This should start the server or connect to the server depending on the implementation.
         */
        override fun initiate() {
            if (!connection.isAlive) throw SocketException("Connection [${connection.uuid}] is not alive")
            running.set(true)
        }

        override fun terminate() {
            running.set(false)
            logger.info { "ClientWorker terminated" }
        }


        /**
         * Sends a packet to the server.
         */
        override fun send(packet: Packet, id: Connection?) {
            try {
                val target = id?.uuid?.let { targetOf(it) } ?: AllPlayersTarget
                MinecraftNetworkAdapter.sendPacket(packet, target)
            } catch (ex: SocketException) {
                logger.warn { "$connection is not alive" }
                this@Server.disconnected(connection)
                running.set(false)
            }
        }
        /**
         * Delegates the packet to the server.
         */
        override fun sendToAll(packet: Packet, vararg exclude: UUID) = this@Server.sendToAll(packet, *exclude)


        /**
         * Retrieves the Connection associated with the specified connection ID.
         *
         * @param connectionID The ID of the connection to retrieve.
         * @return The Connection object associated with the given connection ID, or null if no such connection exists.
         */
        override fun get(connectionID: UUID): Connection? {
            if (connectionID == connection.uuid) return connection
            return null
        }
        /**
         * Disconnects the specified user by their ID.
         *
         * @param id the ID of the user to be disconnected
         */
        override fun disconnected(id: Connection) {
            running.set(false)
            this@Server.disconnected(id)
        }

    }

    companion object {

        internal inline operator fun <reified Result : Any?> invoke(noinline optBlk: (Server.() -> Result)? = null): Server {
            val server = if (serverRef.get() == null) {
                //Create the server, which initializes the serverRef to this server instance
                Server()
            } else serverRef.get() as Server
            if (optBlk != null) server.optBlk()
            return server
        }
    }
}