package noderspace.client.runtime

import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiConfigFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import noderspace.client.font.FontType
import noderspace.client.font.Fonts
import noderspace.client.runtime.renders.Renderspace
import noderspace.client.runtime.renders.Window
import noderspace.client.runtime.windows.CanvasContext
import noderspace.client.runtime.windows.CanvasWindow
import noderspace.client.runtime.windows.ProjectListWindow
import noderspace.client.theme.DarkTheme
import noderspace.common.logging.KotlinLogging
import noderspace.common.managers.Heartbearts
import noderspace.common.managers.Schemas
import noderspace.common.network.Client
import noderspace.common.network.Listener
import noderspace.common.network.NetUtils
import noderspace.common.network.Network.new
import noderspace.common.packets.Packet
import noderspace.common.utils.FontAwesome
import noderspace.common.workspace.Workspace
import noderspace.common.workspace.graph.Node
import noderspace.common.workspace.packets.*
import org.lwjgl.glfw.GLFW
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * The `Runtime` interface provides methods for checking the status of keyboard keys and mouse buttons.
 *
 * This should be the only class needing to be implemented for a new platform.
 */
class Runtime(
    /**
     * The client uid that the runtime should use to connect to the server
     */
    private val clientUid: UUID,
) : Listener {

    private var selectionWindow: Window? = null
    private val dockspace = Renderspace("Runtime")
    private var running = false
    private var workspaceWindow: Window? = null
    internal var workspace: Workspace? = null
    private val connectionExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelay = 5L // seconds
    /**This stores the glfw backend implementation for imgui**/
    private lateinit var imGuiGlfw: ImGuiImplGlfw
    private lateinit var imGuiGl3: ImGuiImplGl3
    private var hasRequestedWorkspaceLibrary = false
    private var queuedWorkspaceLibrary: WorkspaceLibrary? = null
    private lateinit var io: ImGuiIO
    /**
     * Represents the currently selected node in the application.
     *
     * The `selectedNode` variable is used to store a reference to the node that is currently selected by the user.
     * This variable can hold a reference to a `Node` object, or be `null` if no node is selected.
     *
     * It is important to note that this variable is nullable, meaning it can hold a null value. This is useful to
     * indicate the absence of a selected node in cases where no node has been explicitly selected.
     *
     * @property selectedNode The currently selected `Node` object, or `null` if no node is selected.
     */
    var selectedNode: Node? = null
        set(value) {
            field = value
            client.send(new<NodeSelected> {
                this.nodeId = value?.uid ?: NetUtils.DefaultUUID
                if (value != null) logger.info { "Selected node: ${value.uid}" }
                else logger.info { "Selected node: null" }
            })
        }

    /**
     * Sets up the client by installing the Heartbeart feature and installing the current instance.
     *
     * @param client The client to be set up.
     */
    private fun createClient(client: Client) =
        client.install(this)
            .install<Heartbearts>()
            .install<Schemas>(Path.of("/Users/jamesraynor/Documents/nodeer/graph-common/src/main/resources/assets/schemas"))
//            .install<Schemas>(Path.of("C:\\Users\\jraynor\\IdeaProjects\\bp\\graph-common\\src\\main\\resources\\assets\\schemas"))
            .install<CanvasContext>()


    /**
     * initialize the runtime with the given window handle
     */
    fun start(windowHandle: Long): Runtime {
        ImGui.createContext();
        configureRenderspace()
        // Initialize fonts
        initializeFonts()
        imGuiGlfw = ImGuiImplGlfw()
        imGuiGl3 = ImGuiImplGl3()
        imGuiGlfw.init(windowHandle, true) // Set to true for callbacks
        imGuiGl3.init("#version 150") // Use version 150 for better compatibility

        Platform.install(windowHandle)
        if (!registerFonts()) {
            logger.error { "Failed to register fonts, exiting..." }
            stop()
            return this
        }

        createClient(Client(clientUid)).start()
        running = true
        logger.info { "Runtime has started" }
        return this
    }

    private fun registerFonts(attempts: Int = 0): Boolean {
        try {
            Fonts.registerFonts()
            return true
        } catch (e: Exception) {
            if (attempts < 3) {
                logger.error { "Failed to register fonts, retrying..." }
                registerFonts(attempts + 1)
            } else {
                logger.error { "Failed to register fonts after 3 attempts" }
            }
        }
        return false
    }

    /**
     * Stop the runtime, we should clean up any resources here and unhook any installed glfw callbacks
     */
    fun stop() {
        client.stop()
        running = false
        logger.warn { "Runtime has been stopped" }
    }

    fun newFrame() {
        imGuiGlfw.newFrame()
        ImGui.newFrame()
    }


    fun endFrame() {
        ImGui.render()
        imGuiGl3.renderDrawData(ImGui.getDrawData())

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            val backupWindowPtr = GLFW.glfwGetCurrentContext()
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()
            GLFW.glfwMakeContextCurrent(backupWindowPtr)
        }
    }
    /**
     * Should be called once per frame to process events from the main thread
     */
    fun process(pollEvents: Boolean = false) {
        if (!running) return

        // Check if we need to request the workspace library
        if (workspace == null && selectionWindow == null && !hasRequestedWorkspaceLibrary) {
            client.send(new<WorkspaceLibraryRequest> {
                logger.info { "Requesting workspace library" }
            })
            hasRequestedWorkspaceLibrary = true
        }

        // Check if we need to show the queued workspace library
        queuedWorkspaceLibrary?.let { workspaceLibrary ->
            if (workspace == null && selectionWindow == null) {
                showWorkspaceLibrary(workspaceLibrary)
                queuedWorkspaceLibrary = null
            }
        }

        // Draw the dockspace
        DarkTheme.themed {
            dockspace.process()
            // Poll events from the main thread
        }

    }

    fun setDisplaySize(width: Float, height: Float) {
        io.displaySize.set(width, height)
        ImGui.getStyle().scaleAllSizes(1.0f)  // Adjust scale if needed
    }

    private fun showWorkspaceLibrary(workspaceLibrary: WorkspaceLibrary) {
        selectionWindow = dockspace.addFloatingWindow(
            "Workspaces", ProjectListWindow(this, workspaceLibrary)
        ).apply {
            val sizes = Platform.frameSize
            centered = true
            size = { sizes.x / 2f to sizes.y / 2 }
        }
        logger.info { "Showing workspace library: ${workspaceLibrary.workspaces}" }
    }


    private fun connectWithRetry(host: String, port: Int) {
        reconnectAttempts = 0
        connectionExecutor.execute {
            while (running && reconnectAttempts < maxReconnectAttempts) {
                try {
                    client.connect(host, port)
                    logger.info { "Connected to server: $host:$port" }
                    reconnectAttempts = 0
                    break
                } catch (e: Exception) {
                    reconnectAttempts++
                    logger.error { "Failed to connect to server (attempt $reconnectAttempts/$maxReconnectAttempts): $e" }
                    if (reconnectAttempts < maxReconnectAttempts) {
                        Thread.sleep(reconnectDelay * 1000)
                    }
                }
            }
            if (reconnectAttempts == maxReconnectAttempts) {
                logger.error { "Failed to connect after $maxReconnectAttempts attempts. Giving up." }
            }
        }
    }

    private fun startConnectionMonitor(host: String, port: Int) {
        connectionExecutor.scheduleAtFixedRate({
            if (running && !client.connected) {
                logger.warn { "Connection lost. Attempting to reconnect..." }
                connectWithRetry(host, port)
            }
        }, 5, 5, TimeUnit.SECONDS)
    }


    fun connect(host: String, port: Int) {
        connectWithRetry(host, port)
        startConnectionMonitor(host, port)
    }

    /**
     * Handles the event when a connection is established.
     *
     * @param uuid The unique identifier of the connected client.
     */
    override fun onConnect(uuid: UUID) {
        client.send(new<WorkspaceLibraryRequest> {
            logger.info { "Requesting workspace library" }
        })
    }

    /**
     * Sets the workspace for the application.
     *
     * @param workspace The workspace to set.
     */
    fun setWorkspace(workspace: UUID) {
        logger.info { "Workspace set to: $workspace" }
        client.send(new<WorkspaceSelected> {
            this.workspaceUid = workspace
        })
    }

    /**
     * Called when a packet is received from the worker thread
     *
     * @param packet the packet that was received
     */
    override fun onPacket(packet: Packet, from: UUID): Unit = when (packet) {
        is WorkspaceLibrary -> {
            // Queue up the workspace library packet
            queuedWorkspaceLibrary = packet
            hasRequestedWorkspaceLibrary = false // Reset the flag
            logger.info { "Received workspace library response: ${packet.workspaces}" }
        }

        is WorkspaceLoad -> {
            this.workspace = packet.workspace
            workspaceWindow = dockspace.addFloatingWindow(
                "Workspace", CanvasWindow(packet.workspace!!, this)
            ).apply {
                size = { Platform.frameWidth / 1.4f to Platform.frameHeight / 1.33f }
            }
            selectionWindow?.let { dockspace.removeFloatingWindow(it) }
            selectionWindow = null
            queuedWorkspaceLibrary = null // Clear any queued workspace library
            logger.info { "Received workspace load response: $packet" }
        }

        is WorkspaceCreateResponsePacket -> {
            if (packet.success) {
                logger.info { "Workspace created successfully: ${packet.workspaceUid}" }
                // Refresh the workspace list
                client.send(new<WorkspaceLibraryRequest> {})
            } else {
                logger.warn { "Failed to create workspace" }
            }
        }

        else -> Unit
    }

    /**
     * Sends a request to create a new workspace.
     *
     * @param name The name of the new workspace.
     * @param description The description of the new workspace.
     */
    fun createWorkspace(name: String, description: String) {
        client.send(new<WorkspaceCreateRequestPacket> {
            this.name = name
            this.description = description
        })
    }


    /**
     * Sets up the ImGui configuration
     */
    private fun configureRenderspace() {
        io = ImGui.getIO()
        io.iniFilename = null
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard)
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable)
        io.configViewportsNoTaskBarIcon = true
        io.configViewportsNoAutoMerge = true

    }

    private fun initializeFonts() {
        try {
            Fonts.register("Inter", 8..50)
            Fonts.register(
                "Fa",
                24..84,
                listOf(FontType.Regular),
                Pair(FontAwesome.IconMin, FontAwesome.IconMax),
                "C:\\Users\\jraynor\\IdeaProjects\\bpm-dev\\src\\main\\resources\\fonts\\Fa-Regular.ttf",
                true
            )
            Fonts.registerFonts()
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize fonts" }
            throw RuntimeException("Font initialization failed", e)
        }
    }


    fun compile(workspace: Workspace) {
        client.send(new<WorkspaceCompileRequest> {
            this.workspaceId = workspace.uid
        })
    }

    fun reloadNodeLibrary() {
        client.send(new<NodeLibraryReloadRequest> {})
    }

    /**
     * Enumeration representing different types of mouse buttons.
     *
     * @property value The integer value associated with the mouse button.
     */
    enum class MouseButton(val value: Int) {

        LEFT(0), RIGHT(1), MIDDLE(2), UNKNOWN(-1);

        @Suppress("EnumValuesSoftDeprecate")
        companion object {

            private val entries = MouseButton.values().associateBy { it.value }
            /**
             * Retrieves the Mouse associated with the given value.
             *
             * @param value The integer value for which to retrieve the Mouse.
             * @return The Mouse associated with the given value, or UNKNOWN if not found.
             */
            fun from(value: Int): MouseButton = this.entries.getOrDefault(value, UNKNOWN)
        }
    }

    /**
     * Represents a key.
     *
     * @param value the value of the key code, we copy glfw's key codes as they are the most common
     */
    enum class Key(val value: Int) {

        SPACE(32), APOSTROPHE(39), COMMA(44), MINUS(45), PERIOD(46), SLASH(47), ZERO(48), ONE(49), TWO(50), THREE(
            51
        ),
        FOUR(52), FIVE(53), SIX(54), SEVEN(55), EIGHT(56), NINE(57), SEMICOLON(59), EQUAL(61), A(65), B(66), C(67), D(68), E(
            69
        ),
        F(70), G(71), H(72), I(73), J(74), K(75), L(76), M(77), N(78), O(79), P(80), Q(81), R(82), S(83), T(84), U(85), V(
            86
        ),
        LEFT_SHIFT(340), RIGHT_SHIFT(344), LEFT_CONTROL(341), RIGHT_CONTROL(345), LEFT_ALT(342), RIGHT_ALT(346), LEFT_SUPER(
            343
        ),
        W(87), X(88), Y(89), Z(90), LEFT_BRACKET(91), BACKSLASH(92), RIGHT_BRACKET(93), GRAVE_ACCENT(96), WORLD_1(161), WORLD_2(
            162
        ),
        ESCAPE(256), ENTER(257), TAB(258), BACKSPACE(259), INSERT(260), DELETE(261), RIGHT(262), LEFT(263), DOWN(264), UP(
            265
        ),
        PAGE_UP(266), PAGE_DOWN(267), HOME(268), END(269), CAPS_LOCK(280), SCROLL_LOCK(281), NUM_LOCK(282), PRINT_SCREEN(
            283
        ),
        PAUSE(284), F1(290), F2(291), F3(292), F4(293), F5(294), F6(295), F7(296), F8(297), F9(298), F10(299), F11(300), F12(
            301
        ),
        F13(302), F14(303), F15(304), F16(305), F17(306), F18(307), F19(308), F20(309);

        @Suppress("EnumValuesSoftDeprecate")
        companion object {

            private val entries = values().associateBy { it.value }
            /**
             * Retrieves the Key associated with the given value.
             *
             * @param value The integer value for which to retrieve the Key.
             * @return The Key associated with the given value, or UNKNOWN if not found.
             */
            fun from(value: Int): Key = this.entries.getOrDefault(value, SPACE)
        }
    }

    companion object {

        private val logger = KotlinLogging.logger { }
    }


}