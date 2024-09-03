package bpm.mc.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

interface MultiBlockComponent {
    val structureType: String
    fun canConnectTo( state: BlockState): Boolean
}
