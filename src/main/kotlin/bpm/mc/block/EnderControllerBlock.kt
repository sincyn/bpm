package bpm.mc.block

import bpm.mc.gui.NodeEditorGui
import bpm.pipe.PipeNetworkManager
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.apache.commons.compress.compressors.lz77support.LZ77Compressor


class EnderControllerBlock(properties: Properties) : BasePipeBlock(properties), EntityBlock {

    private val shape = makeShape()

    override fun useItemOn(
        p_316304_: ItemStack,
        p_316362_: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        p_316595_: InteractionHand,
        p_316140_: BlockHitResult
    ): ItemInteractionResult {
        if (level.isClientSide) {
            val tileEntity = level.getBlockEntity(pos) as? EnderControllerTileEntity
            val uuid = tileEntity?.getUUID()
            println("Opening GUI for controller with UUID: $uuid")
            NodeEditorGui.open(uuid ?: return ItemInteractionResult.FAIL)
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide)
    }


    override fun getCollisionShape(
        p_60572_: BlockState,
        p_60573_: BlockGetter,
        p_60574_: BlockPos,
        p_60575_: CollisionContext
    ): VoxelShape {
        return shape
    }

    override fun getShape(
        p_60555_: BlockState,
        p_60556_: BlockGetter,
        p_60557_: BlockPos,
        p_60558_: CollisionContext
    ): VoxelShape {
        return shape
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val level = context.level
        val pos = context.clickedPos
        if (!level.isClientSide && PipeNetworkManager.hasControllerInNetwork(level, pos)) {
            return null // Prevent placement if there's already a controller in the network
        }
        return super.getStateForPlacement(context)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        super.onPlace(state, level, pos, oldState, isMoving)
        if (!level.isClientSide) {
            PipeNetworkManager.onPipeAdded(this, level, pos)
            // Force an update of the network to ensure it recognizes the new controller
            PipeNetworkManager.updateNetwork(level, pos)
        }
    }


    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        fromPos: BlockPos,
        isMoving: Boolean
    ) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving)
        if (!level.isClientSide) {
            PipeNetworkManager.updateNetwork(level, pos)
        }
    }

    override fun canBeReplaced(state: BlockState, context: BlockPlaceContext): Boolean {
        return false
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val blockEntity = level.getBlockEntity(pos) as? EnderControllerTileEntity
        if (blockEntity != null && !level.isClientSide && !player.isCreative) {
            val stack = ItemStack(this)
            val serverLevel = level as net.minecraft.server.level.ServerLevel
            val registryAccess = serverLevel.registryAccess()
            blockEntity.saveToItem(stack, registryAccess)
            ItemEntity(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, stack).apply {
                setDefaultPickUpDelay()
                level.addFreshEntity(this)
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    fun makeShape(): VoxelShape {
        var shape = Shapes.empty()
        shape = Shapes.join(shape, Shapes.box(0.421875, 0.4375, 0.0, 0.4375, 0.5625, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4375, 0.5625, 0.0, 0.5625, 0.578125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4375, 0.421875, 0.0, 0.5625, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.4375, 0.0, 0.578125, 0.5625, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.421875, 0.5703125, 0.0, 0.4296875, 0.578125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5703125, 0.5703125, 0.0, 0.578125, 0.578125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.421875, 0.421875, 0.0, 0.4296875, 0.4296875, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5703125, 0.421875, 0.0, 0.578125, 0.4296875, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.4296875, 0.0, 0.5703125, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.5625, 0.0, 0.5703125, 0.5703125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4296875, 0.5625, 0.0, 0.4375, 0.5703125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4296875, 0.4296875, 0.0, 0.4375, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.4296875, 0.0, 0.5703125, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4296875, 0.4296875, 0.0, 0.4375, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4375, 0.421875, 0.0, 0.5625, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4375, 0.5625, 0.0, 0.5625, 0.578125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.421875, 0.4375, 0.0, 0.4375, 0.5625, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.4375, 0.0, 0.578125, 0.5625, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.421875, 0.421875, 0.0, 0.4296875, 0.4296875, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.421875, 0.5703125, 0.0, 0.4296875, 0.578125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5703125, 0.421875, 0.0, 0.578125, 0.4296875, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5703125, 0.5703125, 0.0, 0.578125, 0.578125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.5625, 0.0, 0.5703125, 0.5703125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4296875, 0.5625, 0.0, 0.4375, 0.5703125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4296875, 0.4296875, 0.0, 0.4375, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.4296875, 0.0, 0.5703125, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.4296875, 0.0, 0.5703125, 0.4375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.5625, 0.5625, 0.0, 0.5703125, 0.5703125, 1.0), BooleanOp.OR)
        shape = Shapes.join(
            shape,
            Shapes.box(0.4215625, 0.4215625, 0.984375, 0.5787500000000001, 0.5784375000000002, 1.0000625),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.42156250000000006,
                0.42156250000000006,
                -0.00006250000000007638,
                0.5784375000000002,
                0.5787500000000001,
                0.015625
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.4375, 0.4371875, 0.99375, 0.45312500000000006, 0.5621875000000001, 1.003125),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.5465625000000001, 0.4371875, 0.99375, 0.5621875000000001, 0.5621875000000001, 1.003125),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.45281250000000006, 0.4375, 0.99375, 0.5465625000000001, 0.453125, 1.003125),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.45281250000000006, 0.546875, 0.99375, 0.5465625000000001, 0.5625, 1.003125),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.484375, 0.49687500000000007, 1.001625, 0.515625, 0.5343749999999999, 1.0022499999999999),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.4375, 0.4371875, 0.996875, 0.45312500000000006, 0.5621875000000001, 1.00625),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.5465625000000001, 0.4371875, 0.996875, 0.5621875000000001, 0.5621875000000001, 1.00625),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.45281250000000006, 0.4375, 0.996875, 0.5465625000000001, 0.453125, 1.00625),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.45281250000000006, 0.546875, 0.996875, 0.5465625000000001, 0.5625, 1.00625),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.46875, 0.471875, 0.996875, 0.5, 0.5093749999999999, 1.0046875),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.5, 0.471875, 0.99625, 0.53125, 0.5093749999999999, 1.0040624999999999),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.46875, 0.503125, 0.9975, 0.5, 0.5406249999999999, 1.0053125),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.5, 0.503125, 0.998125, 0.53125, 0.5406249999999999, 1.0059375),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.46875, 0.48125, 1.0015625, 0.5, 0.5187499999999999, 1.0021874999999998),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.5, 0.48125, 1.0015625, 0.53125, 0.5187499999999999, 1.0021874999999998),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.4212499999999999,
                0.4215625,
                -0.00006250000000007638,
                0.5784374999999999,
                0.5784375000000002,
                0.015625
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.546875, 0.4371875, -0.006250000000000089, 0.5625, 0.5621875000000001, 0.0031250000000000444),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.43781249999999994,
                0.4371875,
                -0.006250000000000089,
                0.45343749999999994,
                0.5621875000000001,
                0.0031250000000000444
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.45343749999999994,
                0.4375,
                -0.006250000000000089,
                0.5471874999999999,
                0.453125,
                0.0031250000000000444
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.45343749999999994,
                0.546875,
                -0.006250000000000089,
                0.5471874999999999,
                0.5625,
                0.0031250000000000444
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.484375,
                0.49687500000000007,
                0.029000000000000137,
                0.515625,
                0.5343749999999999,
                0.029625000000000012
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.46875, 0.48125, 0.029062500000000213, 0.5, 0.5187499999999999, 0.02968750000000009),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.5, 0.48125, 0.029062500000000213, 0.53125, 0.5187499999999999, 0.02968750000000009),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.46875, 0.471875, -0.004687499999999956, 0.5, 0.5093749999999999, 0.0031250000000000444),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.46875, 0.503125, -0.005312500000000053, 0.5, 0.5406249999999999, 0.0024999999999999467),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.5, 0.471875, -0.004062499999999858, 0.53125, 0.5093749999999999, 0.003750000000000142),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.5, 0.503125, -0.005937499999999929, 0.53125, 0.5406249999999999, 0.001875000000000071),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(0.546875, 0.4371875, -0.0031250000000000444, 0.5625, 0.5621875000000001, 0.006249999999999978),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.43781249999999994,
                0.4371875,
                -0.0031250000000000444,
                0.45343749999999994,
                0.5621875000000001,
                0.006249999999999978
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.45343749999999994,
                0.4375,
                -0.0031250000000000444,
                0.5471874999999999,
                0.453125,
                0.006249999999999978
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.45343749999999994,
                0.546875,
                -0.0031250000000000444,
                0.5471874999999999,
                0.5625,
                0.006249999999999978
            ),
            BooleanOp.OR
        )
        shape = Shapes.join(
            shape,
            Shapes.box(
                0.42156250000000006,
                0.42156250000000006,
                -0.00006250000000007638,
                0.5784375000000002,
                0.5787500000000001,
                0.015625
            ),
            BooleanOp.OR
        )

        return shape
    }

    override fun newBlockEntity(p_153215_: BlockPos, p_153216_: BlockState): BlockEntity? {
        return EnderControllerTileEntity(p_153215_, p_153216_)
    }


}
