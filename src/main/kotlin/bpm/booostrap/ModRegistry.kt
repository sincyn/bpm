package bpm.booostrap

import bpm.mc.multiblock.GlobalMultiBlockRegistry
import bpm.mc.multiblock.MultiBlockStructure
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

interface ModRegistry<T : Any> {

    val registry: DeferredRegister<T>? get() = null

    fun register(modBus: IEventBus) = registry?.register(modBus)

    fun <V : T> register(registrationFunction: () -> DeferredHolder<T, V>): RegistryValueAccessor<T, V> {
        return RegistryValueAccessor<T, V>().also { it.setValue(null, it::holder, registrationFunction()) }
    }

    fun registerStructure(name: String, registerFunction: (Level, BlockPos)-> MultiBlockStructure) {
        GlobalMultiBlockRegistry.registerStructureType(name, registerFunction)
    }
}