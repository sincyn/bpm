package bpm.mc.registries

import bpm.Bpm
import bpm.mc.block.EnderControllerTileEntity
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

object ModTiles {

    val BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Bpm.ID)

    val ENDER_CONTROLLER_TILE_ENTITY = BLOCK_ENTITIES.register("ender_pipe_controller") { _ ->
        BlockEntityType.Builder.of(::EnderControllerTileEntity, ModBlocks.ENDER_CONTROLLER.get()).build(null)
    }

    fun register(modBus: IEventBus) {
        BLOCK_ENTITIES.register(modBus)
    }
}