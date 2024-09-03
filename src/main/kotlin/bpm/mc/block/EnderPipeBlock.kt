package bpm.mc.block

import bpm.mc.multiblock.GlobalMultiBlockRegistry
import bpm.mc.multiblock.pipe.PipeBaseBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class EnderPipeBlock(properties: Properties) : PipeBaseBlock(properties) {

    private val visualShapeCache = mutableMapOf<Int, VoxelShape>()
    private val collisionShapeCache = mutableMapOf<Int, VoxelShape>()

    // Visual shapes (same as before)
    private val CENTER_SHAPE = Shapes.box(0.375, 0.375, 0.375, 0.625, 0.625, 0.625)
    private val NORTH_SHAPE = Shapes.box(0.375, 0.375, 0.0, 0.625, 0.625, 0.375)
    private val SOUTH_SHAPE = Shapes.box(0.375, 0.375, 0.625, 0.625, 0.625, 1.0)
    private val WEST_SHAPE = Shapes.box(0.0, 0.375, 0.375, 0.375, 0.625, 0.625)
    private val EAST_SHAPE = Shapes.box(0.625, 0.375, 0.375, 1.0, 0.625, 0.625)
    private val UP_SHAPE = Shapes.box(0.375, 0.625, 0.375, 0.625, 1.0, 0.625)
    private val DOWN_SHAPE = Shapes.box(0.375, 0.0, 0.375, 0.625, 0.375, 0.625)

    // Simplified collision shapes
    private val COLLISION_CENTER = Shapes.box(0.3125, 0.3125, 0.3125, 0.6875, 0.6875, 0.6875)
    private val COLLISION_NORTH = Shapes.box(0.3125, 0.3125, 0.0, 0.6875, 0.6875, 0.6875)
    private val COLLISION_SOUTH = Shapes.box(0.3125, 0.3125, 0.3125, 0.6875, 0.6875, 1.0)
    private val COLLISION_WEST = Shapes.box(0.0, 0.3125, 0.3125, 0.6875, 0.6875, 0.6875)
    private val COLLISION_EAST = Shapes.box(0.3125, 0.3125, 0.3125, 1.0, 0.6875, 0.6875)
    private val COLLISION_UP = Shapes.box(0.3125, 0.3125, 0.3125, 0.6875, 1.0, 0.6875)
    private val COLLISION_DOWN = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.6875, 0.6875)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return getCachedVisualShape(state)
    }

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return getCachedCollisionShape(state)
    }

    private fun getCachedVisualShape(state: BlockState): VoxelShape {
        val cacheKey = getShapeCacheKey(state)
        return visualShapeCache.getOrPut(cacheKey) { createVisualShapeForState(state) }
    }

    private fun getCachedCollisionShape(state: BlockState): VoxelShape {
        val cacheKey = getShapeCacheKey(state)
        return collisionShapeCache.getOrPut(cacheKey) { createCollisionShapeForState(state) }
    }

    private fun getShapeCacheKey(state: BlockState): Int {
        var key = 0
        for (direction in Direction.values()) {
            if (state.getValue(getPropertyForDirection(direction))) {
                key = key or (1 shl direction.ordinal)
            }
        }
        return key
    }
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        if (!canConnectToAny(context.level, context.clickedPos)) {
            return null
        }
        return getUpdatedState(context.level, context.clickedPos, defaultBlockState())
    }


    private fun canConnectToAny(level: Level, pos: BlockPos): Boolean {
        var adjacentControllers = 0
        var adjacentNetworksWithController = 0
        var adjacentPipes = 0

        for (direction in Direction.entries) {
            val neighborPos = pos.relative(direction)
            val neighborState = level.getBlockState(neighborPos)
            val neighborBlock = neighborState.block

            when {
                neighborBlock is EnderControllerBlock -> {
                    adjacentControllers++
                }
                neighborBlock is PipeBaseBlock -> {
                    adjacentPipes++
                    if (networkHasController(level, neighborPos)) {
                        adjacentNetworksWithController++
                    }
                }
            }
        }

        // Disallow placement if there are multiple adjacent controllers
        if (adjacentControllers > 1) {
            return false
        }

        // Disallow placement if there are multiple adjacent networks with controllers
        if (adjacentNetworksWithController > 1) {
            return false
        }

        // Disallow placement if there's an adjacent network with a controller and an adjacent controller
        if (adjacentNetworksWithController == 1 && adjacentControllers == 1) {
            return false
        }

        // Allow placement if there are adjacent pipes or if it's being placed in air
        return adjacentPipes > 0 || adjacentPipes == 0
    }





    private fun createVisualShapeForState(state: BlockState): VoxelShape {
        var shape = CENTER_SHAPE
        if (state.getValue(NORTH)) shape = Shapes.join(shape, NORTH_SHAPE, BooleanOp.OR)
        if (state.getValue(SOUTH)) shape = Shapes.join(shape, SOUTH_SHAPE, BooleanOp.OR)
        if (state.getValue(WEST)) shape = Shapes.join(shape, WEST_SHAPE, BooleanOp.OR)
        if (state.getValue(EAST)) shape = Shapes.join(shape, EAST_SHAPE, BooleanOp.OR)
        if (state.getValue(UP)) shape = Shapes.join(shape, UP_SHAPE, BooleanOp.OR)
        if (state.getValue(DOWN)) shape = Shapes.join(shape, DOWN_SHAPE, BooleanOp.OR)
        return shape
    }

    private fun createCollisionShapeForState(state: BlockState): VoxelShape {
        var shape = COLLISION_CENTER
        if (state.getValue(NORTH)) shape = Shapes.join(shape, COLLISION_NORTH, BooleanOp.OR)
        if (state.getValue(SOUTH)) shape = Shapes.join(shape, COLLISION_SOUTH, BooleanOp.OR)
        if (state.getValue(WEST)) shape = Shapes.join(shape, COLLISION_WEST, BooleanOp.OR)
        if (state.getValue(EAST)) shape = Shapes.join(shape, COLLISION_EAST, BooleanOp.OR)
        if (state.getValue(UP)) shape = Shapes.join(shape, COLLISION_UP, BooleanOp.OR)
        if (state.getValue(DOWN)) shape = Shapes.join(shape, COLLISION_DOWN, BooleanOp.OR)
        return shape
    }

}