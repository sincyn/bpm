package bpm.mc.registries

import bpm.Bpm
import bpm.mc.block.UUIDSerializer
import net.minecraft.core.registries.Registries
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.*
import java.util.function.Supplier

object ModAttachments {

    val ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Bpm.ID)

    val UUID_ATTACHMENT = ATTACHMENTS.register("uuid", Supplier {
        AttachmentType.builder(Supplier { UUID.randomUUID() })
            .serialize(UUIDSerializer)
            .build()
    })

    fun register(modbus: IEventBus) {
        ATTACHMENTS.register(modbus)
    }
}