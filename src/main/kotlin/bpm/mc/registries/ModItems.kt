package bpm.mc.registries

import bpm.Bpm
import bpm.mc.item.EnderControllerItem
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister


object ModItems {

    val ITEMS = DeferredRegister.createItems(Bpm.ID)

    val ENDER_CONTROLLER = ITEMS.registerItem("ender_pipe_controller") {
        EnderControllerItem()
    }

    val ENDER_PROXY = ITEMS.registerItem("ender_pipe_proxy") {
        BlockItem(ModBlocks.ENDER_PROXY.get(), Item.Properties())
    }

    val ENDER_PIPE = ITEMS.registerItem("ender_pipe") {
        BlockItem(ModBlocks.ENDER_PIPE.get(), Item.Properties())
    }


    fun register(modBus: IEventBus) {
        ITEMS.register(modBus)
    }
}