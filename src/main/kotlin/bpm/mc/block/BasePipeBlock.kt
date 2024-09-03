package bpm.mc.block

import bpm.pipe.PipeNetworkManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.neoforged.neoforge.common.extensions.IBlockExtension

open class BasePipeBlock(properties: Properties) : Block(properties), IBlockExtension {
    companion object {
        val NORTH: BooleanProperty = BooleanProperty.create("north")
        val EAST: BooleanProperty = BooleanProperty.create("east")
        val SOUTH: BooleanProperty = BooleanProperty.create("south")
        val WEST: BooleanProperty = BooleanProperty.create("west")
        val UP: BooleanProperty = BooleanProperty.create("up")
        val DOWN: BooleanProperty = BooleanProperty.create("down")
        val PROXY = BooleanProperty.create("proxy")
        val ALL_STATES = listOf(NORTH, EAST, SOUTH, WEST, UP, DOWN, PROXY)
    }

    init {
        registerDefaultState(stateDefinition.any().apply {
            ALL_STATES.forEach { setValue(it, false) }
        })
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(*ALL_STATES.toTypedArray())
    }


    override fun getStateForPlacement(p_49820_: BlockPlaceContext): BlockState? {
        return getUpdatedState(p_49820_.level, p_49820_.clickedPos, defaultBlockState())
    }

    open fun canConnectTo(level: Level, pos: BlockPos, direction: Direction): Boolean {
        val neighborState = level.getBlockState(pos.relative(direction))
        return neighborState.block is BasePipeBlock
    }

    protected open fun getUpdatedState(level: Level, pos: BlockPos, currentState: BlockState): BlockState {
        var newState = currentState.setValue(PROXY, false) // Reset proxy state
        var connections = mutableListOf<Direction>()

        for (direction in Direction.values()) {
            val canConnect = canConnectTo(level, pos, direction)
            if (canConnect) {
                connections.add(direction)
            }
            newState = newState.setValue(getPropertyForDirection(direction), false) // Reset all connection states
        }

        when {
            connections.isEmpty() -> {
                // If there are no connections, set proxy to true and UP to true
                newState = newState.setValue(PROXY, true).setValue(UP, true)
            }
            connections.size == 1 -> {
                // If there's only one connection, set the proxy state and the corresponding direction
                newState = newState.setValue(PROXY, true).setValue(getPropertyForDirection(connections[0]), true)
            }
            else -> {
                // Otherwise, set the connection states for all connections
                for (direction in connections) {
                    newState = newState.setValue(getPropertyForDirection(direction), true)
                }
            }
        }

        return newState
    }

    protected open fun notifyNeighbors(level: Level, pos: BlockPos) {
        for (direction in Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this)
        }
    }

    protected fun getPropertyForDirection(direction: Direction): BooleanProperty {
        return when (direction) {
            Direction.NORTH -> NORTH
            Direction.EAST -> EAST
            Direction.SOUTH -> SOUTH
            Direction.WEST -> WEST
            Direction.UP -> UP
            Direction.DOWN -> DOWN
        }
    }

//    open fun onPipeAdded(level: Level, pos: BlockPos) {
//        PipeNetworkManager.onPipeAdded(this, level, pos)
//    }
//
//    open fun onPipeRemoved(level: Level, pos: BlockPos) {
//        PipeNetworkManager.onPipeRemoved(this, level, pos)
//    }
//
//
//    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
//        super.onPlace(state, level, pos, oldState, isMoving)
//        if (!level.isClientSide) {
//            PipeNetworkManager.onPipeAdded(this, level, pos)
//        }
//    }
//
//    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
//        if (!level.isClientSide && state.block != newState.block) {
//            PipeNetworkManager.onPipeRemoved(this, level, pos)
//        }
//        super.onRemove(state, level, pos, newState, isMoving)
//    }
}