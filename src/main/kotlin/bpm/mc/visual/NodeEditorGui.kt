package bpm.mc.visual

import bpm.Bpm
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import noderspace.client.runtime.ClientRuntime
import noderspace.common.network.Client
import noderspace.common.workspace.packets.NodeLibraryRequest
import noderspace.common.workspace.packets.WorkspaceCompileRequest
import noderspace.common.workspace.packets.WorkspaceSelected
import java.util.*

class NodeEditorGui(private val workspaceUuid: UUID) : Screen(Component.literal("Node Editor")) {

    private var openTime: Long = 0

    override fun init() {
        super.init()
        openTime = System.currentTimeMillis()
        Client {
            send(NodeLibraryRequest())
            send(WorkspaceSelected(workspaceUuid))
        }
        Overlay2D.skipped = true
        ClientRuntime.openCanvas()
    }

    override fun render(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        try {
            ClientRuntime.newFrame()
            ClientRuntime.process()
            ClientRuntime.endFrame()
        } catch (e: Exception) {
            Bpm.LOGGER.error("Error rendering NodeEditorGui", e)
        }
    }

    override fun onClose() {
        super.onClose()
        //Fix for nodes not being removed when closing the gui
        ClientRuntime.closeCanvas()
        Overlay2D.skipped = false
        Client { send(WorkspaceCompileRequest(workspaceUuid)) }
    }

    override fun isPauseScreen(): Boolean = false

    companion object {

        fun open(uuid: UUID) {
            Minecraft.getInstance().setScreen(NodeEditorGui(uuid))
        }
    }
}