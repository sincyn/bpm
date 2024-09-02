package bpm

import bpm.mc.visual.Overlay2D
import bpm.mc.registries.ModAttachments
import bpm.mc.registries.ModBlocks
import bpm.mc.registries.ModItems
import bpm.mc.registries.ModTiles
import bpm.mc.visual.Overlay3D
import bpm.network.MinecraftNetworkAdapter
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import noderspace.client.runtime.ClientRuntime
import noderspace.common.managers.Schemas
import noderspace.common.network.Endpoint
import noderspace.common.network.Server
import noderspace.server.environment.ServerRuntime
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture

@Mod(Bpm.ID)
object Bpm {

    const val ID = "bpm"
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {

        ModBlocks.register(MOD_BUS)
        ModItems.register(MOD_BUS)
        ModTiles.register(MOD_BUS)
        ModAttachments.register(MOD_BUS)
        MOD_BUS.addListener(Bpm::onRegisterPayloads)
        runForDist(clientTarget = {
            FORGE_BUS.addListener(Bpm::onClientPlayerLogin)
            FORGE_BUS.addListener(Bpm::onClientPlayerLogout)
            MOD_BUS.addListener(Bpm::onClientSetup)
            MOD_BUS.addListener(Bpm::onRegisterClientReloadListeners)
            FORGE_BUS.addListener(Bpm::renderOverlay2D)
            FORGE_BUS.addListener(Bpm::renderOverlay3D)
            Minecraft.getInstance()
        }, serverTarget = {
            "server"
        })
        //Server setup, should be done on client too for single player
        MOD_BUS.addListener(Bpm::onServerSetup)
    }

    private fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        runForDist(clientTarget = {
            ClientRuntime.client.registerSerializers()
        }, { })
        MinecraftNetworkAdapter.registerPayloads(event)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Client starting...")
    }


    private fun onRegisterClientReloadListeners(event: RegisterClientReloadListenersEvent) {
        event.registerReloadListener { pPreparationBarrier, _, _, _, pBackgroundExecutor, pGameExecutor ->
            CompletableFuture.runAsync({
            }, pBackgroundExecutor).thenCompose { pPreparationBarrier.wait(null) }.thenAcceptAsync({
                LOGGER.log(Level.INFO, "Initializing EditorContext...")
                ClientRuntime.start(Minecraft.getInstance().window.window)
            }, pGameExecutor)
        }
    }

    @OnlyIn(Dist.CLIENT)
    private fun renderOverlay2D(event: RenderGuiEvent.Pre) {
        if (Overlay2D.skipped) return
        ClientRuntime.newFrame()
        Overlay2D.render()
        ClientRuntime.endFrame()
    }

    @OnlyIn(Dist.CLIENT)
    private fun renderOverlay3D(event: RenderLevelStageEvent) =
        if (event.stage == RenderLevelStageEvent.Stage.AFTER_LEVEL)
            Overlay3D.render(
                event.levelRenderer,
                event.poseStack,
                event.projectionMatrix,
                event.modelViewMatrix,
                event.camera,
                event.frustum
            )
        else Unit

    @OnlyIn(Dist.CLIENT)
    private fun onClientPlayerLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        LOGGER.log(Level.INFO, "Client player logging in: ${event.player?.name}")
        //TODO: if already loaded, don't reload
        // This runs on the game thread
        //The local players uuiid
        ClientRuntime.connect()
    }

    //Cutout rendering

    @OnlyIn(Dist.CLIENT)
    private fun onClientPlayerLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
//        LOGGER.log(Level.INFO, "Client player logging out: ${event.player?.name}")
        ClientRuntime.disconnect()
    }


    //On connect to server, start the ClientRuntime.

    private fun onServerSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Server starting...")
        //TODO: get the minecraft assets  path and load schemas through resources
        Server {
            install<ServerRuntime>()
            install<Schemas>(
                Path.of("C:\\Users\\jraynor\\IdeaProjects\\bpm-dev\\src\\main\\resources\\schemas"),
                Endpoint.Side.SERVER
            )
        }.start()
    }
}