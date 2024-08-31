package noderspace.common.managers

import noderspace.common.logging.KotlinLogging
import noderspace.common.network.Client
import noderspace.common.network.Listener
import noderspace.common.network.Network.new
import noderspace.common.packets.Packet
import noderspace.common.packets.internal.DisconnectPacket
import noderspace.common.packets.internal.Heartbeat
import noderspace.common.packets.internal.Time
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * The HeartBeatManager class is responsible for managing heartbeat information from connected devices.
 * It implements the NetListener interface to handle connection, disconnection, and packet events.
 */
object Heartbearts : Listener {

    private val logger = KotlinLogging.logger {}
    private val heartbeats: ConcurrentMap<UUID, Heartbeat> = ConcurrentHashMap()

    private val ownHeartbeat = new<Heartbeat> {}

    /**
     * This method is called when a connection with a unique identifier (UUID) is established.
     *
     * @param uuid The unique identifier (UUID) of the connected device.
     */
    override fun onConnect(uuid: UUID) {
        synchronized(heartbeats) {
            val heartbeat = heartbeats.getOrPut(uuid) {
                logger.info { "Sent new heartbeat for: '$uuid', set to ${Time.now.futureSeconds(Heartbeat.HEARTBEAT_TIMEOUT_SECONDS)}" }
                new<Heartbeat>() {}
            }
            heartbeat.reschedule(Heartbeat.HEARTBEAT_TIMEOUT_SECONDS)
            worker.endpoint.send(heartbeat)
        }
    }

    /**
     * Called when a packet is received.
     *
     * @param packet the packet that was received
     */
    override fun onPacket(packet: Packet, from: UUID) = synchronized(heartbeats) {
        if (packet is Heartbeat) {
            val heartbeat = heartbeats.getOrPut(from) {
                logger.info { "Added heartbeat for $from - ${packet.timestamp}, now - ${Time.now}" }
                packet
            }
            heartbeat.reschedule(Heartbeat.HEARTBEAT_TIMEOUT_SECONDS)
        }
    }


    /**
     * Executes the logic for each tick of the game loop.
     *
     * @param delta The elapsed time since the last tick, in milliseconds.
     * @return Nothing is explicitly returned.
     */
    override fun onTick(delta: Float, tick: Int) = synchronized(heartbeats) {
        //Only tick if connected
        if (worker.isClient && !(worker.endpoint as Client).connected) return
        heartbeats.forEach(Heartbearts::processHeartbeat)
        val expires = ownHeartbeat.expiresIn
        if (worker.isClient && expires <= 1000) {
            ownHeartbeat.reschedule(Heartbeat.HEARTBEAT_TIMEOUT_SECONDS)
            worker.endpoint.send(ownHeartbeat)
            val now = Time.now
            val leaseTime = now until ownHeartbeat.timestamp
            logger.info {
                "Sent new heartbeat, now: $now, until:  ${ownHeartbeat.timestamp}, lease time: ${
                    leaseTime.toString(
                        true
                    )
                }"
            }
        }
    }

    /**
     * Processes a heartbeat for a given UUID and Heartbeat object.
     *
     * If the heartbeat is after the current time and the endpoint is a server, the client is disconnected.
     * If the heartbeat is before the current time - 2.5 seconds and the endpoint is a client, a heartbeat is sent.
     *
     * @param uuid The UUID of the heartbeating entity.
     * @param heartbeat The heartbeat object containing the scheduled checkup time.
     */
    private fun processHeartbeat(uuid: UUID, heartbeat: Heartbeat) {
        // If the heartbeat is after the current time, and the endpoint is a server (we're a server) disconnect the client
        if (heartbeat.isExpired() && worker.isServer) {
            logger.info { "Disconnecting $uuid due to inactivity" }
            //Send disconnect packet
            worker.endpoint.send(new<DisconnectPacket>() {
                this.uuid = uuid
            }!!)
            worker.endpoint.disconnect(uuid)
            //TODO: This may need to be synchronized or done from a queue
            heartbeats.remove(uuid)
        }
    }

    /**
     * Called when a user with the given UUID disconnects from the system.
     *
     * @param uuid The UUID of the user who disconnected.
     */
    override fun onDisconnect(uuid: UUID) = synchronized(heartbeats) {
        val removed = heartbeats.remove(uuid) ?: return
        logger.info { "Removed heartbeat for $uuid, $removed" }
    }


}