package bpm

import bpm.mc.registries.ModBlocks
import bpm.mc.registries.ModItems
import net.minecraft.client.Minecraft
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import noderspace.client.runtime.Runtime
import noderspace.common.managers.Heartbearts
import noderspace.common.managers.Schemas
import noderspace.common.network.Server
import noderspace.server.environment.Environment
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

@Mod(Bpm.ID)
object Bpm {

    const val ID = "bpm"
    val LOGGER: Logger = LogManager.getLogger(ID)


    @OnlyIn(Dist.CLIENT)
    lateinit var runtime: Runtime
        private set

    init {
        LOGGER.log(Level.INFO, "Hello world!")

        ModBlocks.register(MOD_BUS)
        ModItems.register(MOD_BUS)
        runForDist(clientTarget = {
            FORGE_BUS.addListener(Bpm::onClientPlayerLogin)
            FORGE_BUS.addListener(Bpm::onClientPlayerLogout)
            MOD_BUS.addListener(Bpm::onClientSetup)
            MOD_BUS.addListener(Bpm::onRegisterClientReloadListeners)
            Minecraft.getInstance()
        }, serverTarget = {
            MOD_BUS.addListener(Bpm::onServerSetup)
            "server"
        })
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Client starting...")

    }

    private fun onRegisterClientReloadListeners(event: RegisterClientReloadListenersEvent) {
        event.registerReloadListener(object : PreparableReloadListener {
            override fun reload(
                pPreparationBarrier: PreparableReloadListener.PreparationBarrier,
                pResourceManager: ResourceManager,
                pPreparationsProfiler: ProfilerFiller,
                pReloadProfiler: ProfilerFiller,
                pBackgroundExecutor: Executor,
                pGameExecutor: Executor
            ): CompletableFuture<Void> {
                return CompletableFuture.runAsync({
                    // This runs on the background thread
                    LOGGER.log(Level.INFO, "Preparing EditorContext initialization...")
                }, pBackgroundExecutor).thenCompose { pPreparationBarrier.wait(null) }.thenAcceptAsync({
                    val uuid = Minecraft.getInstance().player?.uuid ?: UUID.randomUUID()
                    runtime = Runtime(
                        clientUid = uuid
                    )
                    LOGGER.log(Level.INFO, "Initializing EditorContext...")
                    runtime.start(Minecraft.getInstance().window.window)
                }, pGameExecutor)
            }
        })
    }

    @OnlyIn(Dist.CLIENT)
    private fun onClientPlayerLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        LOGGER.log(Level.INFO, "Client player logging in: ${event.player?.name}")
        //TODO: if already loaded, don't reload
        // This runs on the game thread
        //The local players uuiid
        if (this::runtime.isInitialized) {
            runtime.connect("localhost", 33456)
        } else {
            LOGGER.log(Level.WARN, "Runtime not initialized when player logged in")
        }
    }

    //Cutout rendering

    @OnlyIn(Dist.CLIENT)
    private fun onClientPlayerLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
//        LOGGER.log(Level.INFO, "Client player logging out: ${event.player?.name}")
//        if (this::runtime.isInitialized) {
//            runtime.stop()
//        } else {
//            LOGGER.log(Level.WARN, "Runtime not initialized when player logged out")
//        }
    }


    //On connect to server, start the runtime.

    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
        //TODO: get the minecraft assets  path and load schemas through resources
        val server = Server(33456)
            .install<Heartbearts>()
            .install<Environment>()
            .install<Schemas>(Path.of("C:\\Users\\jraynor\\IdeaProjects\\bpm-dev\\src\\main\\resources\\schemas"))
        server.start()
    }
}