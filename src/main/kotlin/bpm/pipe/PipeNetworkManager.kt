package bpm.pipe

import bpm.mc.block.BasePipeBlock
import bpm.mc.block.EnderControllerBlock
import bpm.mc.block.EnderControllerTileEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.references.Blocks
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.state.BlockState
import noderspace.common.logging.KotlinLogging
import noderspace.server.environment.Environment
import java.util.concurrent.ConcurrentHashMap

object PipeNetworkManager {

    private val networks = mutableListOf<PipeNetwork>()
    private val pipeTypeCache = ConcurrentHashMap<Class<out BasePipeBlock>, MutableSet<BlockPos>>()
    private val logger = KotlinLogging.logger {}
    fun onPipeAdded(pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        val connectedNetworks = findConnectedNetworks(level, pos)
        when {
            connectedNetworks.isEmpty() -> createNetwork(pipe, level, pos)
            connectedNetworks.size == 1 -> connectedNetworks.first().addPipe(pipe, level, pos)
            else -> mergeNetworks(connectedNetworks, pipe, level, pos)
        }

        if (pipe is EnderControllerBlock) {
            onControllerPlaced(level.getBlockEntity(pos) as EnderControllerTileEntity)
        }
        addToTypeCache(pipe, pos)
    }


    private fun onControllerRemoved(entity: EnderControllerTileEntity) {
        val uuid = entity.getUUID()
        //We should locate the workspace in the environment, and remove any event functions that are associated with the controller
        Environment.closeWorkspace(uuid)
    }


    private fun onControllerPlaced(entity: EnderControllerTileEntity) {
        val uuid = entity.getUUID()
        //We should locate the workspace in the environment, and add any event functions that are associated with the controller
//        Environment.openWorkspace(uuid)
        //We should recompile the workspace at this point
        Environment.recompileWorkspace(uuid)
    }

    fun onPipeRemoved(pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        val affectedNetworks = findConnectedNetworks(level, pos)

        if (pipe is EnderControllerBlock) {
            val controllerTileEntity = level.getBlockEntity(pos) as? EnderControllerTileEntity
            if (controllerTileEntity != null) {
                onControllerRemoved(controllerTileEntity)
            } else {
                logger.warn("Couldn't remove controller at $pos, no tile entity found")
            }
        }


        affectedNetworks.forEach { network ->
            network.removePipe(level, pos)
            if (network.isEmpty()) {
                networks.remove(network)
            } else {
                val newNetworks = network.split(level, pos)
                if (newNetworks.size > 1) {
                    networks.remove(network)
                    networks.addAll(newNetworks)
                }
            }
        }

        removeFromTypeCache(pipe, pos)
    }


    private fun addToTypeCache(pipe: BasePipeBlock, pos: BlockPos) {
        pipeTypeCache.getOrPut(pipe::class.java) { ConcurrentHashMap.newKeySet() }.add(pos)
    }

    private fun removeFromTypeCache(pipe: BasePipeBlock, pos: BlockPos) {
        pipeTypeCache[pipe::class.java]?.remove(pos)
    }

    fun getPipesOfType(pipeType: Class<out BasePipeBlock>): Set<BlockPos> {
        return pipeTypeCache[pipeType] ?: emptySet()
    }

    private fun findConnectedNetworks(level: Level, pos: BlockPos): List<PipeNetwork> {
        return networks.filter { network ->
            Direction.entries.any { direction ->
                network.contains(level, pos.relative(direction))
            }
        }
    }

    private fun createNetwork(pipe: BasePipeBlock, level: Level, pos: BlockPos): PipeNetwork {
        val network = PipeNetwork()
        network.addPipe(pipe, level, pos)
        networks.add(network)
        return network
    }

    private fun mergeNetworks(networksToMerge: List<PipeNetwork>, pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        val mergedNetwork = PipeNetwork()
        networksToMerge.forEach { network ->
            mergedNetwork.pipes.putAll(network.pipes)
            networks.remove(network)
        }
        mergedNetwork.addPipe(pipe, level, pos)
        networks.add(mergedNetwork)
    }

    fun hasControllerInNetwork(level: Level, posIn: BlockPos): Boolean {
        val connectedNetworks = findConnectedNetworks(level, posIn)
        return connectedNetworks.any { network ->
            network.pipes.any { (pos, pipe) -> pipe is EnderControllerBlock && pos != posIn }
        }
    }

    fun updateNetwork(level: Level, pos: BlockPos) {
        val blockState = level.getBlockState(pos)

        if (blockState.isAir) {
            // Handle air block case
            onPipeRemoved(level.getBlockState(pos.below()).block as? BasePipeBlock ?: return, level, pos)
            return
        }
        val block = blockState.block
        if (block !is BasePipeBlock) {
            // If the block is neither air nor a BasePipeBlock, we don't need to update the network
            return
        }

        val affectedNetworks = findConnectedNetworks(level, pos)

        if (affectedNetworks.isEmpty()) {
            // If no networks are found, create a new one
            createNetwork(block, level, pos)
        } else {
            // Merge networks if more than one is found
            if (affectedNetworks.size > 1) {
                mergeNetworks(affectedNetworks, block, level, pos)
            }

            // Update the single (possibly merged) network
            val network = affectedNetworks.first()
            network.updateConnections(level)

            // Check for multiple controllers
            val controllers = network.pipes.filter { it.value is EnderControllerBlock }
            if (controllers.size > 1) {
                // Remove all but the first controller
                controllers.keys.drop(1).forEach { controllerPos ->
                    val controllerState = level.getBlockState(controllerPos)
                    val controllerBlock = controllerState.block
                    if (controllerBlock is EnderControllerBlock) {
                        onPipeRemoved(controllerBlock, level, controllerPos)
                        level.removeBlock(controllerPos, false)
                    }
                }
            }
        }
    }


}