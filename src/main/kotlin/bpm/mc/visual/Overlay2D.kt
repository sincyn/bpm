package bpm.mc.visual

import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import noderspace.client.runtime.windows.NotificationManager
import noderspace.common.network.Listener
import noderspace.common.packets.Packet
import noderspace.common.workspace.packets.NotifyMessage
import java.util.*

object Overlay2D : Listener {

    var skipped = false
    private val notificationManager: NotificationManager = NotificationManager()
    fun render() {
        val mainViewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(mainViewport.posX, mainViewport.posY)
        ImGui.setNextWindowSize(mainViewport.sizeX, mainViewport.sizeY)
        ImGui.begin(
            "Overlay",
            ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoScrollbar or ImGuiWindowFlags.NoScrollWithMouse or ImGuiWindowFlags.NoBringToFrontOnFocus or ImGuiWindowFlags.NoNavFocus
                    or ImGuiWindowFlags.NoBackground or ImGuiWindowFlags.NoDocking or ImGuiWindowFlags.NoSavedSettings or ImGuiWindowFlags.NoNav
        )


        val drawList = ImGui.getWindowDrawList()
        val displaySize = ImGui.getIO().displaySize
        notificationManager.renderNotifications(drawList, displaySize)
        ImGui.text("Hello, world!")

        ImGui.end()
    }

    override fun onPacket(packet: Packet, from: UUID) {
        if (packet is NotifyMessage) {
            notificationManager.addNotification(packet)
        }
    }
}