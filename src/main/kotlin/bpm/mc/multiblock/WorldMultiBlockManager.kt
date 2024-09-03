package bpm.mc.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import noderspace.common.logging.KotlinLogging
import java.util.*

class WorldMultiBlockManager(private val level: Level) : IWorldMultiBlockManager {
    private val logger = KotlinLogging.logger {}
    private val structures = mutableMapOf<String, MultiBlockStructure>()
    private val posToStructure = mutableMapOf<BlockPos, MultiBlockStructure>()

    override fun getStructure(pos: BlockPos): MultiBlockStructure? {
        return posToStructure[pos]
    }

    override fun getStructure(id: String): MultiBlockStructure? = structures[id]

    override fun addStructure(structure: MultiBlockStructure) {
        structures[structure.structureType] = structure
        structure.getAllComponents().keys.forEach { pos ->
            posToStructure[pos] = structure
        }
    }

    override fun removeStructure(pos: BlockPos) {
        val structure = posToStructure[pos] ?: return
        structure.removeComponent(pos, false)
        posToStructure.remove(pos)

        if (structure.getAllComponents().isEmpty()) {
            removeStructure(structure.structureType)
        } else {
            splitStructure(structure, pos)
        }
    }

    private fun splitStructure(structure: MultiBlockStructure, removedPos: BlockPos) {
        val components = structure.getAllComponents().toMutableMap()
        components.remove(removedPos)

        val newStructures = mutableListOf<Map<BlockPos, MultiBlockComponent>>()

        while (components.isNotEmpty()) {
            val startPos = components.keys.first()
            val connectedComponents = findConnectedComponents(startPos, components.keys)
            val structureComponents = connectedComponents.associateWith { components[it] as MultiBlockComponent }
            newStructures.add(structureComponents)
            components.keys.removeAll(connectedComponents)
        }

        if (newStructures.size > 1) {
            // The structure needs to be split
            removeStructure(structure.structureType)

            newStructures.forEach { componentMap ->
                val newOrigin = componentMap.keys.first()
                val newStructure = GlobalMultiBlockRegistry.createStructure(structure.structureType, level, newOrigin)
                if (newStructure != null) {
                    componentMap.forEach { (pos, block) ->
                        newStructure.addComponent(pos, block as Block, false)
                        posToStructure[pos] = newStructure
                    }
                    addStructure(newStructure)
                    newStructure.onStructureChanged()
                } else {
                    logger.error { "Failed to create new structure during split operation" }
                }
            }
        } else {
            // The structure doesn't need to be split, just update it
            structure.onStructureChanged()
        }
    }

    private fun findConnectedComponents(startPos: BlockPos, components: Set<BlockPos>): Set<BlockPos> {
        val connected = mutableSetOf<BlockPos>()
        val queue: Queue<BlockPos> = LinkedList()
        queue.add(startPos)

        while (queue.isNotEmpty()) {
            val currentPos = queue.poll()
            if (connected.add(currentPos)) {
                for (direction in Direction.entries) {
                    val neighborPos = currentPos.relative(direction)
                    if (components.contains(neighborPos) && !connected.contains(neighborPos)) {
                        queue.add(neighborPos)
                    }
                }
            }
        }

        return connected
    }

    override fun removeStructure(id: String) {
        structures.remove(id)?.let { structure ->
            structure.getAllComponents().keys.forEach { pos ->
                posToStructure.remove(pos)
            }
        }
    }

    override fun onBlockChanged(pos: BlockPos) {
        val oldStructure = posToStructure[pos]
        val newState = level.getBlockState(pos)
        val newBlock = newState.block

        oldStructure?.let { removeStructure(pos) }

        if (newBlock is MultiBlockComponent) {
            val adjacentStructure = findAdjacentStructure(pos, newBlock)
            if (adjacentStructure != null) {
                adjacentStructure.addComponent(pos, newBlock)
                posToStructure[pos] = adjacentStructure
            } else {
                // Create a new structure if no adjacent structure found
                val newStructure = GlobalMultiBlockRegistry.createStructure(newBlock.structureType, level, pos)
                if (newStructure != null) {
                    newStructure.addComponent(pos, newBlock)
                    addStructure(newStructure)
                } else {
                    logger.error { "Failed to create structure for block ${newBlock.name.string}. No structure found with type ${newBlock.structureType}" }
                }
            }
        }
    }

    private fun findAdjacentStructure(pos: BlockPos, block: MultiBlockComponent): MultiBlockStructure? {
        for (direction in Direction.entries) {
            val neighborPos = pos.relative(direction)
            val neighborStructure = posToStructure[neighborPos]
            if (neighborStructure != null) {
                val neighborState = level.getBlockState(neighborPos)
                if (block.canConnectTo(neighborState)) {
                    return neighborStructure
                }
            }
        }
        return null
    }
}