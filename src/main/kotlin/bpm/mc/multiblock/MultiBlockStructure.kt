package bpm.mc.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

interface MultiBlockStructure {
    val structureType: String
    val origin: BlockPos
    val level: Level
    fun addComponent(pos: BlockPos, block: Block, triggerUpdate: Boolean = true)
    fun removeComponent(pos: BlockPos, triggerUpdate: Boolean = true)
    fun getComponent(pos: BlockPos): Block?
    fun getAllComponents(): Map<BlockPos, Block>
    fun onStructureChanged()
    fun moveOrigin(newOrigin: BlockPos, triggerUpdate: Boolean = true)
}