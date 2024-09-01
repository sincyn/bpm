package bpm.mc.block

import bpm.mc.registries.ModAttachments.UUID_ATTACHMENT
import bpm.mc.registries.ModTiles
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderOwner
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.attachment.AttachmentHolder
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.attachment.IAttachmentSerializer
import java.util.*
import java.util.function.Supplier

class EnderControllerTileEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModTiles.ENDER_CONTROLLER_TILE_ENTITY.get(), pos, state), IAttachmentHolder,
    HolderOwner<EnderControllerTileEntity> {

    val attachmentHolder = AttachmentHolder.AsField(this)

    companion object {

    }

    override fun onLoad() {
        super.onLoad()

    }

    override fun saveAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.saveAdditional(tag, provider)
        // Save the UUID directly to the main tag
        tag.putUUID("UUID", getUUID())
        // Save other attachments
        attachmentHolder.serializeAttachments(provider)?.let {
            tag.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, it)
        }
    }


    override fun loadAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        if (tag.hasUUID("UUID")) {
            setUUID(tag.getUUID("UUID"))
        }
        if (tag.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY)) {
            attachmentHolder.deserializeInternal(provider, tag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY))
        }
    }

    override fun getUpdateTag(provider: HolderLookup.Provider): CompoundTag {
        val tag = super.getUpdateTag(provider)
        // Include UUID in the update tag
        tag.putUUID("UUID", getUUID())
        attachmentHolder.serializeAttachments(provider)?.let {
            tag.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, it)
        }
        return tag
    }

    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket? {
        return ClientboundBlockEntityDataPacket.create(this)
    }


    fun getUUID(): UUID = getData(UUID_ATTACHMENT)

    fun setUUID(newUUID: UUID) {
        setData(UUID_ATTACHMENT, newUUID)
        setChanged()
    }
}

object UUIDSerializer : IAttachmentSerializer<CompoundTag, UUID> {

    override fun write(data: UUID, provider: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putUUID("value", data)
        return tag
    }

    override fun read(holder: IAttachmentHolder, nbt: CompoundTag, provider: HolderLookup.Provider): UUID {
        return nbt.getUUID("value")
    }
}