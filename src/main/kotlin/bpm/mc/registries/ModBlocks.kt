package bpm.mc.registries

import bpm.Bpm
import bpm.mc.block.EnderControllerBlock
import bpm.mc.block.EnderPipeBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES

object ModBlocks {

    val BLOCKS = DeferredRegister.createBlocks(Bpm.ID)

    val NODE_EDITOR_BLOCK = BLOCKS.registerBlock("node_controller", {
        EnderControllerBlock(BlockBehaviour.Properties.of().strength(3.0f))
    }, BlockBehaviour.Properties.of().strength(3.0f))

    val ENDER_PIPE = BLOCKS.registerBlock("ender_pipe", {
        EnderPipeBlock(BlockBehaviour.Properties.of().strength(3.0f))
    }, BlockBehaviour.Properties.of().strength(3.0f))

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
    }
}