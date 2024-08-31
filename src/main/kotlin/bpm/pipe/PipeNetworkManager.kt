package bpm.pipe

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
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
        val network = networks.find { it.contains(level, pos) } ?: return
        network.removePipe(level, pos)
        if (network.isEmpty()) {
            networks.remove(network)
        } else {
            val newNetworks = network.split(level, pos)
            networks.addAll(newNetworks)
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
            Direction.values().any { direction ->
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
}