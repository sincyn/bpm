package bpm.mc.registries

import bpm.Bpm
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister


object ModItems {

    val ITEMS = DeferredRegister.createItems(Bpm.ID)

    val NODE_CONTROLLER = ITEMS.registerItem("node_controller") {
        BlockItem(ModBlocks.NODE_EDITOR_BLOCK.get(), Item.Properties())
    }

    val ENDER_PIPE = ITEMS.registerItem("ender_pipe") {
        BlockItem(ModBlocks.ENDER_PIPE.get(), Item.Properties())
    }


    fun register(modBus: IEventBus) {
        ITEMS.register(modBus)
    }
}