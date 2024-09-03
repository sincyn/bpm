package bpm.mc.multiblock

import net.minecraft.world.level.block.Block
/**
 * Extension property to get the structure type of a block
 */
val Block.structureType: String get() = (this as? MultiBlockComponent)?.structureType ?: ""