package bpm

import bpm.mc.gui.Overlay
import bpm.mc.gui.Overlay.skipped
import bpm.mc.registries.ModAttachments
import bpm.mc.registries.ModBlocks
import bpm.mc.registries.ModItems
import bpm.mc.registries.ModTiles
import bpm.network.MinecraftNetworkAdapter
import bpm.network.PacketTarget
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.server.packs.resources.PreparableReloadListener
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.profiling.ProfilerFiller
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import noderspace.client.runtime.Runtime
import noderspace.common.managers.Heartbearts
import noderspace.common.managers.Schemas
import noderspace.common.network.Server
import noderspace.common.packets.Packet
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

    val playerUid by lazy { (Minecraft.getInstance().player?.uuid ?: UUID.randomUUID())!! }

    val runtime: Runtime by lazy {
        Runtime(playerUid)
    }


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
            FORGE_BUS.addListener(Bpm::renderOverlay)
            Minecraft.getInstance()
        }, serverTarget = {
            MOD_BUS.addListener(Bpm::onServerSetup)
            "server"
        })
    }

    private fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        runForDist(clientTarget = {
            runtime.client.registerSerializers()
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
                runtime.start(Minecraft.getInstance().window.window)
            }, pGameExecutor)
        }
    }

    @OnlyIn(Dist.CLIENT)
    private fun renderOverlay(event: RenderGuiEvent.Pre) {
        if (Overlay.skipped) return
        runtime.newFrame()
        Overlay.render()
        runtime.endFrame()
    }

    @OnlyIn(Dist.CLIENT)
    private fun onClientPlayerLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        LOGGER.log(Level.INFO, "Client player logging in: ${event.player?.name}")
        //TODO: if already loaded, don't reload
        // This runs on the game thread
        //The local players uuiid
        runtime.connect("localhost", 33456)
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
        val server = Server(33456).install<Heartbearts>().install<Environment>()
            .install<Schemas>(Path.of("C:\\Users\\jraynor\\IdeaProjects\\bpm-dev\\src\\main\\resources\\schemas"))
        server.start()
    }
}