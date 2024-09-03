package bpm.mc.multiblock.pipe

import bpm.mc.block.EnderControllerBlock
import bpm.mc.multiblock.GlobalMultiBlockRegistry
import bpm.mc.multiblock.MultiBlockComponent
import bpm.mc.multiblock.MultiBlockStructure
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty

abstract class PipeBaseBlock(properties: BlockBehaviour.Properties) : Block(properties), MultiBlockComponent {

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
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val level = context.level
        val pos = context.clickedPos
        val defaultState = defaultBlockState()
        return getUpdatedState(level, pos, defaultState)
    }

//     Gets the network at the given position and checks if it contains a controller
    protected fun networkHasController(level: Level, pos: BlockPos): Boolean {
        val network = GlobalMultiBlockRegistry.getStructure(level, pos) ?: return false
        return network.getAllComponents().values.any { it is EnderControllerBlock }
    }

//    fun networkHasController(level: Level, pos: BlockPos): Boolean {
//        val structure = GlobalMultiBlockRegistry.getStructure(level, pos)
//        return if (structure is PipeNetwork) {
//            structure.controllers.isNotEmpty()
//        } else {
//            false
//        }
//    }

    protected open fun getUpdatedState(level: Level, pos: BlockPos, currentState: BlockState): BlockState {
        var newState = currentState.setValue(PROXY, false) // Reset proxy state
        val connections = mutableListOf<Direction>()
//        val ourStructure = GlobalMultiBlockRegistry.getStructure(level, pos) ?: return newState
        for (direction in Direction.entries) {
            val relativeState = level.getBlockState(pos.relative(direction))
            val canConnect = canConnectTo(relativeState)
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


    protected fun getPropertyForDirection(direction: Direction): BooleanProperty {
        return when (direction) {
            Direction.NORTH ->NORTH
            Direction.EAST -> EAST
            Direction.SOUTH -> SOUTH
            Direction.WEST -> WEST
            Direction.UP ->UP
            Direction.DOWN -> DOWN
        }
    }


    override fun canConnectTo( state: BlockState): Boolean {
        return state.block is PipeBaseBlock
    }

    override val structureType: String = "pipe_network"

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        if (!level.isClientSide) {
            val worldManager = GlobalMultiBlockRegistry.getWorldManager(level)
            worldManager.onBlockChanged(pos)
        }
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        //Make sure it's removed from the world before we remove it from the manager as our manager will query the world for the block state
        if (!level.isClientSide && state.block != newState.block) {
            val worldManager = GlobalMultiBlockRegistry.getWorldManager(level)
            worldManager.onBlockChanged(pos)
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }


    override fun neighborChanged(
        state: BlockState, level: Level, pos: BlockPos, block: Block, fromPos: BlockPos, isMoving: Boolean
    ) {
        if (!level.isClientSide) {
            val newState = getUpdatedState(level, pos, state)
            if (newState != state) {
                level.setBlock(pos, newState, 3)
                notifyNeighbors(level, pos)
            }
        }
    }

    protected open fun notifyNeighbors(level: Level, pos: BlockPos) {
        for (direction in Direction.entries) {
            level.updateNeighborsAt(pos.relative(direction), this)
        }
    }

}