package bpm.booostrap

import bpm.Bpm.LOGGER
import bpm.mc.visual.Overlay2D
import bpm.mc.visual.Overlay3D
import bpm.network.MinecraftNetworkAdapter
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import noderspace.client.runtime.ClientRuntime
import noderspace.client.runtime.windows.CanvasContext
import noderspace.common.logging.KotlinLogging
import noderspace.common.network.Client
import noderspace.common.network.Endpoint
import noderspace.common.network.Network
import noderspace.common.network.Server
import noderspace.common.packets.Packet
import noderspace.common.schemas.Schemas
import noderspace.common.serial.Serial
import noderspace.common.serial.Serialize
import noderspace.common.utils.*
import noderspace.server.environment.ServerRuntime
import noderspace.server.environment.lua.LuaBuiltin
import org.apache.logging.log4j.Level
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class Bootstrap(
    private val results: ClassResourceScanner.ScanResults
) : IBoostrap {

    private val logger = KotlinLogging.logger {}
    private val registriesList = mutableListOf<ModRegistry<*>>()
    private val packetsList = mutableListOf<KClass<out Packet>>()
    private val serializableList = mutableListOf<KClass<out Serialize<*>>>()
    private val builtIns = mutableListOf<LuaBuiltin>()
    private val ourResults = results.fromPackages("noderspace", "bpm")

    /**
     * The entry point for the bootstrap.
     */
    override fun collect(): Bootstrap {
        logger.info("Boostrappin' BPM")
        collectRegistries()
        collectPackets()
        collectSerializable()
        return this
    }
    //Registers all of the serializerables and packet handlers
    override fun register(bus: IEventBus): IBoostrap {
        registerRegistries(bus)
        registerModSpecificEvents(bus)
        return this
    }

    private fun registerModSpecificEvents(modBus: IEventBus) {
        modBus.addListener(::onRegisterPayloads)
        runForDist(clientTarget = {
            FORGE_BUS.addListener(::onClientPlayerLogin)
            FORGE_BUS.addListener(::onClientPlayerLogout)
            modBus.addListener(::onClientSetup)
            modBus.addListener(::onRegisterClientReloadListeners)
            FORGE_BUS.addListener(::renderOverlay2D)
            FORGE_BUS.addListener(::renderOverlay3D)
            Minecraft.getInstance()
        }, serverTarget = {
            "server"
        })
        //Server setup, should be done on client too for single player
        modBus.addListener(::onCommonSetup)
    }

    private fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {

        logger.info("Registering serializers")
        registerSerializers()
        logger.info("Registered packets")
        registerPackets()
        MinecraftNetworkAdapter.registerPayloads(event)
    }

    private fun registerRegistries(bus: IEventBus) {
        registriesList.forEach { it.register(bus) }
    }

    private fun registerSerializers() {
        val serializers = serializableList.associateWith { it.objectInstance ?: it.createInstance() }

        serializers.forEach { (kClass, instance) ->
            Serial.register(kClass.java, instance)
            logger.info("Registered serializer for ${kClass.simpleName}")
        }
    }

    private fun registerPackets() = packetsList.forEach(Network::register)


    private fun onRegisterClientReloadListeners(event: RegisterClientReloadListenersEvent) {
        event.registerReloadListener { pPreparationBarrier, _, _, _, pBackgroundExecutor, pGameExecutor ->
            CompletableFuture.runAsync({}, pBackgroundExecutor).thenCompose { pPreparationBarrier.wait(null) }
                .thenAcceptAsync({
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
        if (event.stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) Overlay3D.render(
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
        // This runs on the game thread
        //The local players uuiid
        ClientRuntime.connect()
    }

    //Cutout rendering

    @OnlyIn(Dist.CLIENT)
    private fun onClientPlayerLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
//        LOGGER.log(Level.INFO, "Client player logging out: ${event.player?.name}")
//        ClientRuntime.disconnect()
    }


    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Server client...")
        val gameDir = FMLPaths.GAMEDIR.get()
        val schemaPath = gameDir.resolve("schemas")
        if (!schemaPath.toFile().exists()) {
            schemaPath.toFile().mkdir()
        }

        Client.install(ClientRuntime).install<Schemas>(schemaPath, Endpoint.Side.CLIENT).install<CanvasContext>()
            .install<Overlay2D>()
    }

    //Copies the schemas from the jar to the game directory
    private fun copySchemas() {
        val gameDir = FMLPaths.GAMEDIR.get()
        val schemaPath = gameDir.resolve("schemas")
        if (!schemaPath.toFile().exists()) {
            schemaPath.toFile().mkdir()
        }

        results

        //Collects the schemas from the jar
        val resources = results.withExtension("node").readResourcesToByteArrayMap()
        resources.forEach { (name, bytes) ->
            val file = schemaPath.resolve(name)
            if (!file.toFile().exists()) {
                file.toFile().writeBytes(bytes)
            }
        }


    }


    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        logger.info("Copying schemas to game directory")
        copySchemas()
        LOGGER.log(Level.INFO, "Scanning for classes...")
        val gameDir = FMLPaths.GAMEDIR.get()
        val schemaPath = gameDir.resolve("schemas")
        if (!schemaPath.toFile().exists()) {
            schemaPath.toFile().mkdir()
        }
        //We initialize this here because it's available on the client too for single player
        Server.install<ServerRuntime>().install<Schemas>(
            schemaPath, Endpoint.Side.SERVER
        ).start()
    }


    //Tries to find the constructor that takes no arguments, if it fails, it will try to find the first constructor
    //that takes the booststrap instance as an argument, then it will call it with our instance.
    private fun createOrGetModRegistryInstance(clazz: KClass<*>): ModRegistry<*> {
        val result = try {
            clazz.objectInstance ?: clazz.constructors.first().call()
        } catch (e: Exception) {
            clazz.constructors.first { it.parameters.size == 1 && it.parameters[0].type.classifier == Bootstrap::class }
                .call(this)
        }
        if (result is ModRegistry<*>) {
            return result
        }
        throw IllegalStateException("Class $clazz is not a ModRegistry")
    }

    private fun collectLuaBuildIng() {
        ourResults.classesImplementing<LuaBuiltin>().forEach {
            builtIns.add(it.objectInstance ?: it.createInstance())
        }
    }

    private fun collectRegistries() {
        logger.info("Collecting registries")
        val modRegistries = ourResults.classesImplementing<ModRegistry<*>>().map { createOrGetModRegistryInstance(it) }

        registriesList.addAll(modRegistries)

        logger.info("Collected ${modRegistries.size} registries")
    }

    private fun collectPackets() {
        logger.info("Collecting packets")
        val packets = ourResults.classesImplementing<Packet>()
        packetsList.addAll(packets)
        logger.info("Collected ${packets.size} packets")
    }

    private fun collectSerializable() {
        logger.info("Collecting serializable")
        val serializable = ourResults.classesImplementing<Serialize<*>>()
        serializableList.addAll(serializable)
        logger.info("Collected ${serializable.size} serializable")
    }

    override val registries: List<ModRegistry<*>> = registriesList
    override val packets: List<KClass<out Packet>> = packetsList
    override val serializable: List<KClass<out Serialize<*>>> = serializableList

}

