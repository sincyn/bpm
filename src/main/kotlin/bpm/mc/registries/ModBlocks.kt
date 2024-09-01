package bpm.mc.registries

import bpm.Bpm
import bpm.mc.block.EnderControllerBlock
import bpm.mc.block.EnderPipeBlock
import bpm.mc.block.EnderProxyBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

// THIS LINE IS REQUIRED FOR USING PROPERTY DELEGATES

object ModBlocks {

    val BLOCKS = DeferredRegister.createBlocks(Bpm.ID)

    val ENDER_CONTROLLER = BLOCKS.registerBlock("ender_pipe_controller", {
        EnderControllerBlock(BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.AMETHYST_CLUSTER).requiresCorrectToolForDrops())
    }, BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.AMETHYST_CLUSTER).requiresCorrectToolForDrops())

    val ENDER_PROXY = BLOCKS.registerBlock("ender_pipe_proxy", {
        EnderProxyBlock(BlockBehaviour.Properties.of())
    }, BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.AMETHYST_CLUSTER).requiresCorrectToolForDrops())

    val ENDER_PIPE = BLOCKS.registerBlock("ender_pipe", {
        EnderPipeBlock(BlockBehaviour.Properties.of())
    }, BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.AMETHYST_CLUSTER).requiresCorrectToolForDrops())

    fun register(modBus: IEventBus) {
        BLOCKS.register(modBus)
    }
}