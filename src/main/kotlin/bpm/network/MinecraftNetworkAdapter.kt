package bpm.network

import bpm.Bpm
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.handling.IPayloadHandler
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import net.neoforged.neoforge.server.ServerLifecycleHooks
import noderspace.common.memory.Buffer
import noderspace.common.network.Endpoint
import noderspace.common.network.NetUtils
import noderspace.common.network.Network
import noderspace.common.packets.Packet
import noderspace.common.serial.Serial

object MinecraftNetworkAdapter {

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(Bpm.ID).versioned("1.0.0")

        for (packetType in Network.registeredTypes) {
            registerPayload(registrar, packetType.java)
        }
    }

    private fun <T : Packet> registerPayload(registrar: PayloadRegistrar, packetType: Class<T>) {
        val type = MinecraftPacketPayload.typeOf(packetType)
        val codec = createCodec(packetType)
        val handler = MinecraftPacketHandler(packetType)
        registrar.playBidirectional(type, codec, handler)
    }

    private fun <T : Packet> createCodec(packetType: Class<T>): StreamCodec<FriendlyByteBuf, MinecraftPacketPayload<T>> {
        return object : StreamCodec<FriendlyByteBuf, MinecraftPacketPayload<T>> {
            override fun decode(buf: FriendlyByteBuf): MinecraftPacketPayload<T> {
                val buffer = Buffer.wrap(buf.readByteArray())
                val packet = Network.new(packetType.kotlin) ?: error("Failed to create packet")
                packet.deserialize(buffer)
                return MinecraftPacketPayload(packet as T)
            }


            override fun encode(buf: FriendlyByteBuf, instance: MinecraftPacketPayload<T>) {
                val buffer = Buffer.allocate()
                instance.packet.serialize(buffer)
                buf.writeByteArray(buffer.finish())
            }
        }
    }

    fun sendPacket(packet: Packet, target: PacketTarget) {
        val payload = MinecraftPacketPayload(packet)
        when (target) {
            is ServerTarget -> PacketDistributor.sendToServer(payload)
            is PlayerTarget -> PacketDistributor.sendToPlayer(target.player, payload)
            is AllPlayersTarget -> PacketDistributor.sendToAllPlayers(payload)
            is NearbyPlayersTarget -> PacketDistributor.sendToPlayersNear(
                target.dimension.level,
                null,
                target.pos.x,
                target.pos.y,
                target.pos.z,
                target.radius,
                payload
            )
        }
    }

    class MinecraftPacketPayload<T : Packet>(val packet: T) : CustomPacketPayload {


        companion object {

            fun <T : Packet> typeOf(packetClass: Class<T>): CustomPacketPayload.Type<MinecraftPacketPayload<T>> =
                CustomPacketPayload.Type(
                    ResourceLocation.fromNamespaceAndPath(
                        Bpm.ID,
                        packetClass.simpleName.lowercase()
                    )
                )
        }

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = typeOf(packet.javaClass)
    }

    class MinecraftPacketHandler<T : Packet>(private val packetClass: Class<T>) :
        IPayloadHandler<MinecraftPacketPayload<T>> {

        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        override fun handle(payload: MinecraftPacketPayload<T>, context: IPayloadContext) {
            val endpoint = Endpoint.get()
            when (context.flow()) {
                PacketFlow.CLIENTBOUND -> {
                    endpoint.worker.queue(payload.packet, context.player().uuid)
                }

                PacketFlow.SERVERBOUND -> {
                    endpoint.worker.queue(payload.packet, NetUtils.DefaultUUID)
                }
            }

        }


    }
}


private val server by lazy { ServerLifecycleHooks.getCurrentServer() ?: error("Server not available") }

val ResourceKey<Level>.level: ServerLevel
    get() {
        val level = server.getLevel(this)
        return level ?: error("Level $this not found")
    }

sealed class PacketTarget
object ServerTarget : PacketTarget()
class PlayerTarget(val player: ServerPlayer) : PacketTarget()
object AllPlayersTarget : PacketTarget()
class NearbyPlayersTarget(val pos: Vec3, val radius: Double, val dimension: ResourceKey<Level>) : PacketTarget()

