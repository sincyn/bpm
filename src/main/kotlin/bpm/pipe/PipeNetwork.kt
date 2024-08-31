package bpm.pipe
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
class PipeNetwork {
    val pipes = mutableMapOf<BlockPos, BasePipeBlock>()

    fun addPipe(pipe: BasePipeBlock, level: Level, pos: BlockPos) {
        pipes[pos] = pipe
    }

    fun removePipe(level: Level, pos: BlockPos) {
        pipes.remove(pos)
    }

    fun contains(level: Level, pos: BlockPos): Boolean = pos in pipes

    fun isEmpty(): Boolean = pipes.isEmpty()

    fun split(level: Level, removedPos: BlockPos): List<PipeNetwork> {
        val newNetworks = mutableListOf<PipeNetwork>()
        val remainingPipes = pipes.toMutableMap()
        remainingPipes.remove(removedPos)

        while (remainingPipes.isNotEmpty()) {
            val startPos = remainingPipes.keys.first()
            val newNetwork = PipeNetwork()
            val connectedPipes = findConnectedPipes(level, startPos, remainingPipes)
            connectedPipes.forEach { (pos, pipe) ->
                newNetwork.addPipe(pipe, level, pos)
                remainingPipes.remove(pos)
            }
            newNetworks.add(newNetwork)
        }

        return newNetworks
    }

    private fun findConnectedPipes(level: Level, start: BlockPos, availablePipes: Map<BlockPos, BasePipeBlock>): Map<BlockPos, BasePipeBlock> {
        val connectedPipes = mutableMapOf<BlockPos, BasePipeBlock>()
        val queue = ArrayDeque<BlockPos>()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentPipe = availablePipes[current] ?: continue
            connectedPipes[current] = currentPipe

            for (direction in Direction.values()) {
                val neighborPos = current.relative(direction)
                if (neighborPos in availablePipes && neighborPos !in connectedPipes) {
                    queue.add(neighborPos)
                }
            }
        }

        return connectedPipes
    }
}