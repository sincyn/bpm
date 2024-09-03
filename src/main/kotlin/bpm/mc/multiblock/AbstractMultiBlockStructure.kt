package bpm.mc.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block


abstract class AbstractMultiBlockStructure(
    override val structureType: String,
    override val origin: BlockPos,
    override val level: Level
) : MultiBlockStructure {

    private val components = mutableMapOf<BlockPos, Block>()

    override fun addComponent(pos: BlockPos, block: Block, triggerUpdate: Boolean) {
        components[pos] = block
        if (triggerUpdate) onStructureChanged()
    }

    override fun removeComponent(pos: BlockPos, triggerUpdate: Boolean) {
        components.remove(pos)
        if (triggerUpdate) onStructureChanged()
    }

    override fun getComponent(pos: BlockPos): Block? = components[pos]

    override fun getAllComponents(): Map<BlockPos, Block> = components.toMap()

    override fun onStructureChanged() {
        // Default implementation, can be overridden
    }

    override fun moveOrigin(newOrigin: BlockPos, triggerUpdate: Boolean) {
        val diff = newOrigin.subtract(origin)
        val newComponents = components.mapKeys { (pos, _) -> pos.offset(diff) }
        components.clear()
        components.putAll(newComponents)
        if(triggerUpdate)  onStructureChanged()

    }
}