package bpm.mc.multiblock.pipe

import bpm.mc.block.EnderControllerBlock
import bpm.mc.block.EnderControllerTileEntity
import bpm.mc.multiblock.AbstractMultiBlockStructure
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents.CUSTOM_NAME
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import noderspace.common.logging.KotlinLogging

class PipeNetwork(level: Level, origin: BlockPos) : AbstractMultiBlockStructure("pipe_network", origin, level) {
    private val logger = KotlinLogging.logger {}
    //Only should have one of these, if multiple are found, drop one of them on the floor

    val controllers: List<EnderControllerBlock>
        get() = getAllComponents().values.filterIsInstance<EnderControllerBlock>()

    override fun onStructureChanged() {
        //Check if there's more than one controller in the network
        logger.info { "Pipe network structure changed" }
        if (controllers.size > 1) {
            logger.info { "More than one controller in network!" }
        }
    }

    private fun dropController(level: Level, pos: BlockPos) {
        val block = level.getBlockState(pos).block
        val blockEntity = level.getBlockEntity(pos) as? EnderControllerTileEntity
        if (blockEntity != null && !level.isClientSide) {
            val stack = ItemStack(block)
            val serverLevel = level as net.minecraft.server.level.ServerLevel
            val registryAccess = serverLevel.registryAccess()
            stack.set(
                CUSTOM_NAME,
                Component.literal("Ender Controller").withStyle(ChatFormatting.DARK_PURPLE)
                    .withStyle(ChatFormatting.BOLD)
            )
            blockEntity.saveToItem(stack, registryAccess)
            ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack).apply {
                setDefaultPickUpDelay()
                level.addFreshEntity(this)
            }
        }
    }
}