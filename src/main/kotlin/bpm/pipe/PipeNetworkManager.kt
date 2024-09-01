package bpm.pipe

import bpm.mc.block.BasePipeBlock
import bpm.mc.block.EnderControllerBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.ConcurrentHashMap

object PipeNetworkManager {

    private val networks = mutableListOf<PipeNetwork>()
    private val pipeTypeCache = ConcurrentHashMap<Class<out BasePipeBlock>, MutableSet<BlockPos>>()

    fun onPipeAdded(pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        val connectedNetworks = findConnectedNetworks(level, pos)
        when {
            connectedNetworks.isEmpty() -> createNetwork(pipe, level, pos)
            connectedNetworks.size == 1 -> connectedNetworks.first().addPipe(pipe, level, pos)
            else -> mergeNetworks(connectedNetworks, pipe, level, pos)
        }
        addToTypeCache(pipe, pos)
    }

    fun onPipeRemoved(pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        val affectedNetworks = findConnectedNetworks(level, pos)

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
        val affectedNetworks = findConnectedNetworks(level, pos)

        if (affectedNetworks.isEmpty()) {
            // If no networks are found, create a new one
            if (level.getBlockState(pos).block is BasePipeBlock)
                createNetwork(level.getBlockState(pos).block as BasePipeBlock, level, pos)
        } else {
            // Merge networks if more than one is found
            if (affectedNetworks.size > 1) {
                mergeNetworks(affectedNetworks, level.getBlockState(pos).block as BasePipeBlock, level, pos)
            }

            // Update the single (possibly merged) network
            val network = affectedNetworks.first()
            network.updateConnections(level)

            // Check for multiple controllers
            val controllers = network.pipes.filter { it.value is EnderControllerBlock }
            if (controllers.size > 1) {
                // Remove all but the first controller
                controllers.keys.drop(1).forEach { controllerPos ->
                    if (level.getBlockState(controllerPos).block is EnderControllerBlock) {
                        onPipeRemoved(level.getBlockState(controllerPos).block as BasePipeBlock, level, controllerPos)
                        level.removeBlock(controllerPos, false)
                    }
                }
            }
        }
    }


}