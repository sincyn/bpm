package bpm.pipe

import bpm.mc.block.BasePipeBlock
import bpm.mc.block.EnderControllerBlock
import bpm.mc.block.EnderControllerTileEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import noderspace.common.logging.KotlinLogging
import noderspace.common.network.Listener
import noderspace.server.environment.ServerRuntime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object PipeNetworkManager : Listener {
    private val networks = mutableListOf<PipeNetwork>()
    private val pipeTypeCache = ConcurrentHashMap<Class<out BasePipeBlock>, MutableSet<BlockPos>>()
    private val logger = KotlinLogging.logger {}
    private val mappedControllers = ConcurrentHashMap<UUID, MutableSet<EnderControllerTileEntity>>()
    private val lock = ReentrantLock()
    private val pendingUpdates = mutableSetOf<PipeNetwork.LevelPipe>()
    private val updateInProgress = AtomicBoolean(false)
   fun onPipeAdded(pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        addToTypeCache(pipe, pos)
        queueNetworkUpdate(level, pos)
        logger.info { "Adding pipe at $pos of type ${pipe::class.simpleName}" }
    }


    fun onPipeRemoved(pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        removeFromTypeCache(pipe, pos)
        if (pipe is EnderControllerBlock) {
            val controllerTileEntity = level.getBlockEntity(pos) as? EnderControllerTileEntity
            if (controllerTileEntity != null) {
                onControllerRemoved(controllerTileEntity)
            } else {
                logger.warn { "Couldn't remove controller at $pos, no tile entity found" }
            }
        }
        queueNetworkUpdate(level, pos)
        logger.info { "Removing pipe at $pos of type ${pipe::class.simpleName}" }
    }

    fun queueNetworkUpdate(level: Level, pos: BlockPos) {
        val block = level.getBlockState(pos).block
        if(block !is BasePipeBlock) {
            //We should A), see if the block is air, if so, we should remove it from the network
            //B) if it's not air, we should log a warning
            logger.warn { "Tried to queue network update for non-pipe block at $pos" }
            return
        }
        pendingUpdates.add(PipeNetwork.LevelPipe(level, block, pos))
    }


    override fun onTick(delta: Float, tick: Int) {
        if (updateInProgress.compareAndSet(false, true)) {
            try {
                processPendingUpdates()
            } finally {
                updateInProgress.set(false)
            }
        }
    }

    private fun processPendingUpdates() {
        val updatedPositions = mutableSetOf<BlockPos>()
        val updates = pendingUpdates.toList()
        pendingUpdates.clear()

        for (levelPipe in updates) {
            if (levelPipe.pos in updatedPositions) continue
            updateNetworkAt(levelPipe.level, levelPipe.pos)
            updatedPositions.add(levelPipe.pos)
        }

        cleanupNetworks()
    }

    private fun updateNetworkAt(level: Level, pos: BlockPos) {
        val blockState = level.getBlockState(pos)
        val block = blockState.block

        if (block !is BasePipeBlock) {
            removeFromAllNetworks(level, pos)
            return
        }

        val connectedNetworks = findConnectedNetworks(level, pos)
        when {
            connectedNetworks.isEmpty() -> createNetwork(block, level, pos)
            connectedNetworks.size == 1 -> connectedNetworks.first().addPipe(block, level, pos)
            else -> mergeNetworks(connectedNetworks, block, level, pos)
        }

        if (block is EnderControllerBlock) {
            val entity = level.getBlockEntity(pos) as? EnderControllerTileEntity
            if (entity != null) {
                onControllerPlaced(entity)
            } else {
                logger.warn { "Couldn't add controller at $pos, no tile entity found... queing it up" }
                queueNetworkUpdate(level, pos)
            }
        }
    }


    private fun removeFromAllNetworks(level: Level, pos: BlockPos) {
        networks.forEach { it.removePipe(level, pos) }
    }

    private fun cleanupNetworks() {
        networks.removeAll { it.isEmpty() }
        networks.forEach { it.cleanupGhostPipes() }
    }

    private fun createNetwork(pipe: BasePipeBlock, level: Level, pos: BlockPos): PipeNetwork {
        val network = PipeNetwork()
        network.addPipe(pipe, level, pos)
        networks.add(network)
        logger.info { "Created new network for pipe at $pos" }
        return network
    }

    private fun mergeNetworks(networksToMerge: List<PipeNetwork>, pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        val mergedNetwork = PipeNetwork()
        networksToMerge.forEach { network ->
            network.pipes.forEach { (pipePos, pipeBlock) ->
                mergedNetwork.addPipe(pipeBlock.pipe, level, pipePos)
            }
            networks.remove(network)
        }
        mergedNetwork.addPipe(pipe, level, pos)
        networks.add(mergedNetwork)
        logger.info { "Merged ${networksToMerge.size} networks" }
    }

    private fun findConnectedNetworks(level: Level, pos: BlockPos): List<PipeNetwork> {
        return networks.filter { network ->
            Direction.entries.any { direction ->
                network.contains(level, pos.relative(direction))
            }
        }
    }

    private fun onControllerPlaced(entity: EnderControllerTileEntity) {
        val uuid = entity.getUUID()
        mappedControllers.getOrPut(uuid) { mutableSetOf() }.add(entity)
        ServerRuntime.recompileWorkspace(uuid)
        logger.info { "Controller placed: $uuid" }
    }

    private fun onControllerRemoved(entity: EnderControllerTileEntity) {
        val uuid = entity.getUUID()
        mappedControllers[uuid]?.remove(entity)
        ServerRuntime.closeWorkspace(uuid)
        logger.info { "Controller removed: $uuid" }
    }



    private fun addToTypeCache(pipe: BasePipeBlock, pos: BlockPos) {
        pipeTypeCache.getOrPut(pipe::class.java) { ConcurrentHashMap.newKeySet() }.add(pos)
    }

    private fun removeFromTypeCache(pipe: BasePipeBlock, pos: BlockPos) {
        pipeTypeCache[pipe::class.java]?.remove(pos)
    }

    fun hasControllerInNetwork(level: Level, posIn: BlockPos): Boolean {
        val connectedNetworks = findConnectedNetworks(level, posIn)
        return connectedNetworks.any { network ->
            network.pipes.any { (pos, pipe) -> pipe.pipe is EnderControllerBlock && pos != posIn }
        }
    }

    fun getController(uuid: UUID): EnderControllerTileEntity? {
        return mappedControllers[uuid]?.firstOrNull()
    }

    fun getControllers(): List<EnderControllerTileEntity> {
        return mappedControllers.values.flatten()
    }

    fun getControllerPosition(uuid: UUID): BlockPos? {
        return mappedControllers[uuid]?.firstOrNull()?.blockPos
    }

    fun getControllerPositions(uuid: UUID): List<BlockPos> {
        return mappedControllers[uuid]?.map { it.blockPos } ?: emptyList()
    }

    fun getControllerPositions(): List<BlockPos> {
        return getControllers().map { it.blockPos }
    }


}