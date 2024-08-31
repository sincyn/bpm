package noderspace.server.environment

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.neoforged.neoforge.server.ServerLifecycleHooks
import noderspace.common.network.Endpoint

object World {

    private val mcServer: MinecraftServer by lazy {
        ServerLifecycleHooks.getCurrentServer() ?: throw IllegalStateException("Server not available")
    }

    private val overworld by lazy {
        mcServer.getLevel(Level.OVERWORLD) ?: throw IllegalStateException("Overworld not available")
    }


    @JvmStatic
    fun signalAt(x: Int, y: Int, z: Int): Int {
        val state = overworld.getBlockState(BlockPos(x, y, z))
        //If it's a jukebox, check if it's playing a record
        if (state.block == net.minecraft.world.level.block.Blocks.JUKEBOX) {
            return if (state.getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD)) 15 else 0
        }
        val signal = state.getSignal(overworld, BlockPos(x, y, z), Direction.DOWN)
        return signal
    }

    @JvmStatic
    fun spawnParticle(
        x: Float,
        y: Float,
        z: Float,
        count: Int,
        velX: Float,
        velY: Float,
        velZ: Float,
        spread: Float,
        particleName: String
    ) {
        val particle = getParticleByName(particleName)
        //Position, count, velocity, spread
        overworld.sendParticles(
            particle,
            x.toDouble(),
            y.toDouble(),
            z.toDouble(),
            count,
            velX.toDouble(),
            velY.toDouble(),
            velZ.toDouble(),
            spread.toDouble()
        )
        println("Spawned particle $particleName at $x, $y, $z")
    }

    private fun getParticleByName(name: String): ParticleOptions {
        return when (name.lowercase()) {
            "explosion" -> ParticleTypes.EXPLOSION
            "smoke" -> ParticleTypes.SMOKE
            "flame" -> ParticleTypes.FLAME
            "heart" -> ParticleTypes.HEART
            "crit" -> ParticleTypes.CRIT
            "enchanted_hit" -> ParticleTypes.ENCHANTED_HIT
            "portal" -> ParticleTypes.PORTAL
            "water" -> ParticleTypes.FALLING_WATER
            "lava" -> ParticleTypes.LAVA
            "redstone" -> ParticleTypes.DUST_PLUME
            // Add more particle types as needed
            else -> ParticleTypes.SMOKE // Default particle if name not recognized
        }
    }

}