package bpm.mc.multiblock

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level

object GlobalMultiBlockRegistry {
    private val registeredStructureTypes = mutableMapOf<String, (Level, BlockPos) -> MultiBlockStructure>()
    private val cachedWorldManagers = mutableMapOf<ResourceKey<Level>, WorldMultiBlockManager>()

    fun registerStructureType(id: String, factory: (Level, BlockPos) -> MultiBlockStructure) {
        registeredStructureTypes[id] = factory
    }

    fun createStructure(id: String, level: Level, origin: BlockPos): MultiBlockStructure? {
        return registeredStructureTypes[id]?.invoke(level, origin)
    }

    fun getWorldManager(level: Level): WorldMultiBlockManager {
        return cachedWorldManagers.computeIfAbsent(level.dimension()) { WorldMultiBlockManager(level) }
    }

    fun getStructure(level: Level, pos: BlockPos): MultiBlockStructure? {
        return getWorldManager(level).getStructure(pos)
    }

    fun removeWorldManager(level: Level) {
        cachedWorldManagers.remove(level.dimension())
    }

    fun dropStructure(level: Level, origin: BlockPos) {
        getWorldManager(level).removeStructure(origin)
    }

}