package noderspace.common.network

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

class Server(private val port: Int) : Endpoint<Server>(), Runnable {

    // Create our logger
    private val logger = KotlinLogging.logger {}

    // Stores the connections to the server
    private val clients: ConcurrentMap<UUID, Endpoint<ClientWorker>> = ConcurrentHashMap()
    private var socket: ServerSocket? = null
    private val thread = Thread(this, "Network Server")
    override val worker = Worker(this)
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
            socket = ServerSocket(port)
            thread.start()
            worker.start()
            logger.info { "Server started on port $port" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to start server" }
            return
        }
        //TODO: relay to listeners
    }


    /**
     * Runs the server and accepts incoming client connections.
     */
    override fun run() {
        while (runningRef.get()) {
            try {
                val client = socket?.accept() ?: continue
                val connection = Connection(UUID.randomUUID(), Side.CLIENT, client)
                logger.info { "Starting worker for ${connection.uuid}" }
                val worker = ClientWorker(connection)
                worker.initiate()
                clients[connection.uuid] = worker
                clientExecutors.execute(worker)
            } catch (e: SocketException) {
                runningRef.set(false)
                logger.error(e) { "Failed to accept client connection" }
            }
        }
        terminate()
    }


    /**
     * This should stop the server or disconnect from the server depending on the implementation.
     */
    override fun terminate() {
        runningRef.set(false)
        clients.forEach { (_, worker) -> worker.terminate() }
        socket?.close()
        clientExecutors.shutdown()
        clients.clear()
        //TODO: relay to listeners
        logger.info { "Server terminated" }
    }


    /**
     * Called when a client has connected
     */
    override fun connected(id: Connection) {
        logger.info { "Client ${id.uuid} connected" }
        listeners.forEach {
            it.onConnect(id.uuid)
        }
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
    private inner class ClientWorker(private val connection: Connection) : Endpoint<ClientWorker>(), Runnable {

        private val running: AtomicBoolean = AtomicBoolean(false)
        override val worker get() = this@Server.worker

        private val logger = KotlinLogging.logger {}

        /**
         * When an object implementing interface `Runnable` is used
         * to create a thread, starting the thread causes the object's
         * `run` method to be called in that separately executing
         * thread.
         *
         *
         * The general contract of the method `run` is that it may
         * take any action whatsoever.
         *
         * @see java.lang.Thread.run
         */
        override fun run() {
            while (running.get()) {
                val packet = receive(connection) ?: continue
                when (packet) {
                    is DisconnectPacket -> {
                        disconnected(connection)
                        break
                    }

                    is ConnectRequest -> {
                        clients.remove(connection.uuid)
                        send(new<ConnectResponsePacket> {
                            this.valid = true
                        }, connection)
                        connection.uuid = packet.uuid
                        this@Server.connected(connection)
                        clients[connection.uuid] = this
                    }

                    else -> {
                        worker.queue(packet, connection.uuid)
                    }
                }
            }
            terminate()
        }

        /**
         * This should start the server or connect to the server depending on the implementation.
         */
        override fun initiate() {
            if (!connection.isAlive) throw SocketException("Connection [${connection.uuid}] is not alive")
            running.set(true)
        }

        /**
         * This should stop the server or disconnect from the server depending on the implementation.
         */
        override fun terminate() {
            try {
                if (!connection.socket.isInputShutdown)
                    connection.socket.shutdownInput()
                if (!connection.socket.isOutputShutdown)
                    connection.socket.shutdownOutput()
                if (!connection.socket.isClosed)
                    connection.socket.close()
//                clientExecutors.remove(this)
                running.set(false)
                logger.info { "Terminated worker for ${connection.uuid}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to terminate worker for ${connection.uuid}" }
            }
        }


        /**
         * Sends a packet to the server.
         */
        override fun send(packet: Packet, id: Connection?) {
            try {

                if (!connection.isAlive) throw SocketException("Connection is not alive")
                val output = connection.socket.getOutputStream() ?: throw Exception("Socket is null")
                packet.write(output)
                logger.info { "Sent packet of type ${packet::class.simpleName} with id ${packet.id}" }
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
         * Receives a packet from the specified connection.
         *
         * @param id The connection from which to receive the packet. Default value is null.
         * @return The received packet, or null if no packet is received.
         */
        override fun receive(id: Connection?): Packet? {
            try {

                if (!connection.isAlive) return null
                val input = connection.socket.getInputStream() ?: throw SocketException("Socket is null")
                val packet = input.readPacket() ?: return null
                logger.info { "Received packet of type ${packet::class.simpleName} with id ${packet.id}" }
                return packet
            } catch (ex: SocketException) {
                logger.warn { "$connection is not alive" }
                running.set(false)
                return null
            }

        }
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
//        /**
//         * Compares this object with the specified object for order. Returns zero if this object is equal
//         * to the specified [other] object, a negative number if it's less than [other], or a positive number
//         * if it's greater than [other].
//         */
//        override fun compareTo(other: ClientWorker): Int {
//            return connection.uuid.compareTo(other.connection.uuid)
//        }
    }
}