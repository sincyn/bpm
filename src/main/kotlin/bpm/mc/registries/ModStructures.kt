package bpm.mc.registries

import bpm.booostrap.ModRegistry
import bpm.mc.multiblock.MultiBlockStructure
import bpm.mc.multiblock.pipe.PipeNetwork
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister

//Lazily initialized by the bootstrap reflection system
object ModStructures : ModRegistry<MultiBlockStructure> {

    override fun register(modBus: IEventBus) = registerStructure("pipe_network", ::PipeNetwork)
}