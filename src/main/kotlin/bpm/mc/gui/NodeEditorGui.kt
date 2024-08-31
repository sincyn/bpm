package bpm.mc.gui

import bpm.Bpm
import bpm.Bpm.runtime
import com.mojang.blaze3d.pipeline.TextureTarget
import imgui.ImGui
import imgui.ImGuiIO
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player

class NodeEditorGui(player: Player) : Screen(Component.literal("Node Editor")) {

    private val io: ImGuiIO get() = ImGui.getIO()
    private var textureTarget: TextureTarget? = null
    private var openTime: Long = 0
    private val rightClickDelay: Long = 2000 // 2 seconds in milliseconds

    override fun init() {
        super.init()
        val window = minecraft!!.window
        runtime.setDisplaySize(window.guiScaledWidth.toFloat(), window.guiScaledHeight.toFloat())
        openTime = System.currentTimeMillis()
    }

    override fun render(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        try {
            runtime.newFrame()
            runtime.process()
            runtime.endFrame()
        } catch (e: Exception) {
            Bpm.LOGGER.error("Error rendering NodeEditorGui", e)
        }
    }

    override fun resize(minecraft: Minecraft, width: Int, height: Int) {
        super.resize(minecraft, width, height)
        runtime.setDisplaySize(width.toFloat(), height.toFloat())
    }

    override fun keyPressed(pKeyCode: Int, pScanCode: Int, pModifiers: Int): Boolean {
        io.setKeysDown(pKeyCode, true)
        return super.keyPressed(pKeyCode, pScanCode, pModifiers)
    }

    override fun keyReleased(pKeyCode: Int, pScanCode: Int, pModifiers: Int): Boolean {
        io.setKeysDown(pKeyCode, false)
        return super.keyReleased(pKeyCode, pScanCode, pModifiers)
    }

    override fun mouseMoved(pMouseX: Double, pMouseY: Double) {
        super.mouseMoved(pMouseX, pMouseY)
    }

    override fun mouseClicked(pMouseX: Double, pMouseY: Double, pButton: Int): Boolean {
        if (pButton == 1) {  // 1 is right-click
            val currentTime = System.currentTimeMillis()
            if (currentTime - openTime < rightClickDelay) {
                return true  // Ignore right-clicks before the delay has passed
            }
        }
        io.setMouseDown(pButton, true)
        return super.mouseClicked(pMouseX, pMouseY, pButton)
    }

    override fun mouseReleased(pMouseX: Double, pMouseY: Double, pButton: Int): Boolean {
        io.setMouseDown(pButton, false)
        return super.mouseReleased(pMouseX, pMouseY, pButton)
    }

    override fun mouseScrolled(p_94686_: Double, p_94687_: Double, p_94688_: Double, p_294830_: Double): Boolean {
        io.mouseWheel = p_294830_.toFloat()
        return super.mouseScrolled(p_94686_, p_94687_, p_94688_, p_294830_)
    }

    override fun charTyped(pChar: Char, pModifiers: Int): Boolean {
//        io.addInputCharacter(pChar.toInt())
        return super.charTyped(pChar, pModifiers)
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        fun open(player: Player) {
            Minecraft.getInstance().setScreen(NodeEditorGui(player))
        }
    }
}