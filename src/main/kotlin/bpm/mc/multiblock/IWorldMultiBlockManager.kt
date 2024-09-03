package bpm.mc.multiblock

import net.minecraft.core.BlockPos

/**
 * Interface for managing multi-block structures within a world.
 */
interface IWorldMultiBlockManager {

    /**
     * Retrieves the multi-block structure at the specified position.
     *
     * @param pos The position to check for a multi-block structure.
     * @return The `MultiBlockStructure` located at the specified position, or null if no structure is found.
     */
    fun getStructure(pos: BlockPos): MultiBlockStructure?
    /**
     * Retrieves the MultiBlockStructure associated with the given ID.
     *
     * @param id the unique identifier of the MultiBlockStructure.
     * @return the MultiBlockStructure corresponding to the specified ID, or null if no such structure exists.
     */
    fun getStructure(id: String): MultiBlockStructure?
    /**
     * Adds a MultiBlockStructure to the manager.
     *
     * @param structure The structure to be added.
     */
    fun addStructure(structure: MultiBlockStructure)
    /**
     * Removes the structure located at the specified position.
     *
     * @param pos The position of the structure to be removed.
     */
    fun removeStructure(pos: BlockPos)
    /**
     * Removes a multi-block structure identified by the given ID.
     *
     * @param id The unique identifier of the structure to be removed.
     */
    fun removeStructure(id: String)
    /**
     * Called when a block has changed in the world.
     *
     * This method should be invoked to notify the manager that the block at the given position has been modified.
     *
     * @param pos Position of the block that was changed
     */
    fun onBlockChanged(pos: BlockPos)
}