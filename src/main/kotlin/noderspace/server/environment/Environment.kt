package noderspace.server.environment

import noderspace.common.logging.KotlinLogging
import noderspace.common.managers.Schemas
import noderspace.common.network.Listener
import noderspace.common.network.Network.new
import noderspace.common.network.listener
import noderspace.common.packets.Packet
import noderspace.common.property.Property
import noderspace.common.property.PropertyMap
import noderspace.common.property.configured
import noderspace.common.type.NodeType
import noderspace.common.vm.EvalContext
import noderspace.common.workspace.packets.WorkspaceCreateRequestPacket
import noderspace.common.workspace.packets.WorkspaceCreateResponsePacket
import noderspace.common.workspace.Workspace
import noderspace.common.workspace.graph.Edge
import noderspace.common.workspace.graph.Node
import noderspace.common.workspace.graph.User
import noderspace.common.workspace.packets.*
import org.joml.Vector2f
import org.joml.Vector4i
import java.util.*

object Environment : Listener {

    private val logger = KotlinLogging.logger { }

    /**
     * The `workspaces` variable is a private mutable map that stores instances of the `Workspace` class indexed by their `UUID`.
     *
     * @see Workspace
     *
     * @property workspaces A mutable map where `UUID` keys are associated with `Workspace` values.
     */
    private val workspaces = mutableMapOf<UUID, Workspace>()

    /**
     * Represents the collection of currently opened workspaces.
     *
     * This variable is a mutable map that stores workspace instances associated with unique
     * UUID identifiers. It allows adding, removing, and accessing the workspaces.
     *
     * @property openedWorkspaces The mutable map that contains the opened workspaces. The UUID
     * keys are used to uniquely identify each workspace, while the corresponding values represent
     * the workspace instances.
     */
    private val openedWorkspaces = mutableMapOf<UUID, UUID>()

    /**
     * Represents a collection of users.
     *
     * @property users A mutable map of user IDs to User instances.
     */
    private val users = mutableMapOf<UUID, User>()


    /**
     * The `vm` variable is an instance of the `VM` class, which stands for "Virtual Machine".
     * It is responsible for executing bytecode instructions that represent a sequence of operations.
     * The `VM` class has the following properties and methods:
     * - `stack`: A stack data structure that holds values during the execution of bytecode instructions.
     * - `nativeFunctions`: A map of native functions that can be called within the bytecode instructions.
     * - `bytecodeGenerator`: An instance of the `BytecodeGenerator` class, which is used to generate bytecode for different node types.
     * - `registerNativeFunction(name: String, function: (Map<String, Any>, Map<String, Any>) -> Any?)`: A method for registering a native function with the `vm`.
     * - `execute(nodeName: String, inputs: Map<String, Any>, properties: Map<String, Any>): Map<String, Any>`: A method that executes the bytecode instructions for a specific node
     *  type, with given inputs and properties.
     *
     * The `vm` variable is created with the `VM` constructor taking a `NodeLibrary` as a parameter, where `Schemas.library` is passed as the argument.
     *
     * The `VM` class uses a set of hardcoded instructions, each identified by an opcode.
     * Some of the opcodes include:
     * - `PUSH_FLOAT`: Push a floating-point value onto the stack.
     * - `PUSH_INT`: Push an integer value onto the stack.
     * - `PUSH_STRING`: Push a string value onto the stack.
     * - `PUSH_BOOL`: Push a boolean value onto the stack.
     * - `PUSH_NULL`: Push a null value onto the stack.
     * - `POP`: Pop the top value from the stack.
     * - `ADD`: Pop two values from the stack, add them together, and push the result onto the stack.
     * - `SUB`: Pop two values from the stack, subtract the second one from the first one, and push the result onto the stack.
     * - `MUL`: Pop two values from the stack, multiply them together, and push the result onto the stack.
     * - `DIV`: Pop two values from the stack, divide the first one by the second one, and push the result onto the stack.
     * - `LOAD`: Load a value from a specific index in the stack and push it onto the stack.
     * - `STORE`: Pop a value from the stack and store it at a specific index.
     * - `GET_PROPERTY`: Get the value of a specific property from the `properties` map and push it onto the stack.
     * - `SET_PROPERTY`: Pop a value from the stack and set it as the value of a specific property (does not modify the `properties` map).
     * - `GET_INPUT`: Get the value of a specific input from the `inputs` map and push it onto the stack.
     * - `SET_OUTPUT`: Pop a value from the stack and store it as the value of a specific output in the `outputs` map.
     * - `CALL_NATIVE`: Call a native function by name, passing the `inputs` and `properties` maps as arguments, and push the result onto the stack.
     * - `RET`: Return the `outputs` map and stop execution.
     * - `HALT`: Stop execution.
     */


    /**
     * Clears the existing workspaces list and refreshes it by loading all available workspaces.
     */
    override fun onInstall() {
        val workspaceNames = Workspace.list()
        workspaceNames.forEach { uuid ->
            val workspace = Workspace.load(uuid)
            if (workspace == null) {
                logger.warn { "Failed to load workspace: $uuid" }
                return@forEach
            }
            workspaces[workspace.uid] = workspace
            logger.debug { "Loaded workspace: $uuid" }
        }
    }

    override fun onTick(delta: Float, tick: Int): Unit {
        for (workspace in workspaces.values) {
            if (workspace.needsRecompile) continue
            val result = EvalContext.callFunction(workspace, "Tick")
            if (result.isFailure) {
                logger.error { "Failed to call Tick function: ${result.failure?.error}" }
                workspace.needsRecompile = true
            }
        }


    }

    /**
     * Called when a packet is received.
     *
     * @param packet the packet that was received
     */
    override fun onPacket(packet: Packet, from: UUID) = when (packet) {
        // When the user asks for a list of workspaces, send them the list of workspaces.
        is WorkspaceLibraryRequest -> sendWorkspaces(from)
        is WorkspaceSelected -> openWorkspace(packet.workspaceUid, from)
        is NodeMoved -> broadCastNodeMove(from, packet)
        is WorkspaceCreateRequestPacket -> createWorkspace(packet.name, packet.description, from)
        is NodeCreateRequest -> createNode(
            users[from]?.workspaceUid ?: error("User not in workspace"),
            packet.nodeType,
            packet.position,
        )

        is NodeDeleteRequest -> {
            val workspace = workspaces[users[from]?.workspaceUid ?: error("User not in workspace")]
            workspace?.removeNode(packet.uuid)

            sendToUsersInWorkspace(users[from]?.workspaceUid ?: error("User not in workspace"), new<NodeDeleted> {
                this.uuid = packet.uuid
            })
        }

        is LinkCreateRequest -> {
            val workspace = workspaces[users[from]?.workspaceUid ?: error("User not in workspace")]
            workspace?.addLink(packet.link)
            logger.debug { "Link created: ${packet.link}" }

            sendToUsersInWorkspace(users[from]?.workspaceUid ?: error("User not in workspace"), new<LinkCreated> {
                this.link = packet.link
            })
        }

        is LinkDeleteRequest -> {
            val workspace = workspaces[users[from]?.workspaceUid ?: error("User not in workspace")]
            workspace?.removeLink(packet.uuid)
            sendToUsersInWorkspace(users[from]?.workspaceUid ?: error("User not in workspace"), new<LinkDeleted> {
                this.uuid = packet.uuid
            })
        }

        is WorkspaceCompileRequest -> {
            val workspace = workspaces[packet.workspaceId] ?: error("Workspace not found")
            compileWorkspace(workspace)
        }

        is EdgePropertyUpdate -> {
            val workspace = workspaces[users[from]?.workspaceUid ?: error("User not in workspace")]
                ?: error("Workspace not found")
            updateEdgeProperty(workspace, packet.edgeUid, packet.property, from)
        }

        is VariableCreateRequest -> {
            val workspace = workspaces[users[from]?.workspaceUid ?: error("User not in workspace")]
                ?: error("Workspace not found")
            createVariable(workspace, packet.name, packet.property, from)
        }

        is VariableDeleteRequest -> {
            val workspace = workspaces[users[from]?.workspaceUid ?: error("User not in workspace")]
                ?: error("Workspace not found")
            workspace.removeVariable(packet.name)
            sendToUsersInWorkspace(workspace.uid, new<VariableDeleted> {
                this.name = packet.name
            })
        }

        is VariableUpdateRequest -> {
            val workspace = workspaces[users[from]?.workspaceUid ?: error("User not in workspace")]
                ?: error("Workspace not found")
            workspace.updateVariable(packet.variableName, packet.property["value"])
//            sendToUsersInWorkspace(workspace.uid, new<VariableUpdated> {
//                this.variableName = packet.variableName
//                this.property = packet.property
//            })
            server.sendToAll(new<VariableUpdated> {
                this.variableName = packet.variableName
                this.property = packet.property
            }, from)
        }

        is VariableNodeCreateRequest -> {
            val workspace = workspaces[users[from]?.workspaceUid ?: error("User not in workspace")]
                ?: error("Workspace not found")
            val type = if (packet.type == noderspace.common.workspace.packets.NodeType.GetVariable) "Variables/Get Variable" else "Variables/Set Variable"
            val library = listener<Schemas>().library
            val nodeType = library[type] ?: error("Node type not found")
            val edges = nodeType.properties["edges"] as? Property.Object ?: error("Edges not found")
            val input = edges["name"] as? Property.Object ?: error("Input not found")
            val value = input["value"] as? Property.Object ?: error("Value not found")
            val default = value["default"] as? Property.String ?: error("Default not found")


            //Sets the default to the variable name
            default.set(packet.variableName)

            val node = createFromType(
                workspace,
                nodeType,
                packet.position
            )

            val variableName = packet.variableName
            val variable = workspace.getVariable(variableName)
            if (variable is Property.Null) run {
                logger.warn { "Failed to create node: $type. Variable not found." }
                return
            } else {
                node.properties["value"] = variable
            }

            //send the node
            sendToUsersInWorkspace(workspace.uid, new<NodeCreated> {
                this.node = node
            })

        }

        else -> Unit
    }

    private fun createVariable(workspace: Workspace, name: String, property: PropertyMap, from: UUID) {
        workspace.addVariable(name, property["value"] ?: Property.Null)
        sendToUsersInWorkspace(workspace.uid, new<VariableCreated> {
            this.name = name
            this.property = property
        })
    }

    private fun updateEdgeProperty(workspace: Workspace, edgeUid: UUID, property: Property<*>, sender: UUID) {
        val edge = workspace.getEdge(edgeUid) ?: return
        edge.properties["value"] = property
        server.sendToAll(new<EdgePropertyUpdate> {
            this.edgeUid = edgeUid
            this.property = edge.properties["value"] as PropertyMap
        }, sender)
//        sendToUsersInWorkspace(workspace.uid, new<EdgePropertyUpdate> {
//            this.edgeUid = edgeUid
//            this.property = edge.properties["value"] as PropertyMap
//        })
    }


    private fun compileWorkspace(workspace: Workspace) {
        try {
            workspace.save()
            workspace.needsRecompile = false
            EvalContext.eval(workspace)
            val result = EvalContext.callFunction(workspace, "Run")
            if (result.isFailure) {
                if (result.failure!!.error.contains("Group Run not found")) {
                    workspace.needsRecompile = false
                    return
                } else {
                    logger.error { "Failed to call Run function: ${result.failure?.error}" }
                    sendToUsersInWorkspace(workspace.uid, new<NotifyMessage> {
                        icon = 0xf071
                        message = result.failure?.error ?: "Failed to call Run function"
                        header = "Error: Failed to call Run function"
                        color = "#f54242"
                        lifetime = 2.5f
                        type = NotifyMessage.NotificationType.ERROR
                    })
                    workspace.needsRecompile = true
                }
            }
        } catch (e: Exception) {
            logger.error { "Failed to compile workspace: ${e.message}" }
            sendToUsersInWorkspace(workspace.uid, new<NotifyMessage> {
                icon = 0xf071
                message = e.message ?: "Failed to compile workspace"
                header = "Error: Failed to compile workspace"
                color = "#f54242"
                lifetime = 2.5f
                type = NotifyMessage.NotificationType.ERROR
            })
            workspace.needsRecompile = true
        }

    }

    private fun createNode(workspaceId: UUID, nodeType: String, position: Vector2f) {
        val workspace = workspaces[workspaceId] ?: run {
            logger.warn { "Failed to create node: $workspaceId" }
            return
        }

        val schema = listener<Schemas>().library[nodeType] ?: run {
            logger.warn { "Failed to create node: $nodeType. Unknown type." }
            return
        }
        val node = createFromType(workspace, schema, position)

        sendToUsersInWorkspace(workspaceId, new<NodeCreated> {
            this.node = node
        })
    }

    private fun sendToUsersInWorkspace(workspaceId: UUID, packet: Packet) {
        val usersNotInWorkspace = users.filter { (_, user) ->
            user.workspaceUid != workspaceId
        }.map { (_, user) ->
            user
        }.toMutableList().map { it.uid }.toTypedArray()

        server.sendToAll(packet, *usersNotInWorkspace)
    }

    private fun intToUnicodeEscaped(codePoint: Int): String {
        return "\\u" + codePoint.toString(16).padStart(4, '0')
    }

    private fun createFromType(workspace: Workspace, nodeType: NodeType, position: Vector2f): Node {
        val name = nodeType["name"] as? Property.String ?: Property.String(nodeType.meta.name)
        val theme = nodeType["theme"] as? Property.Object ?: Property.Object()
        val color = theme["color"] as? Property.Vec4i ?: Property.Vec4i(Vector4i(255, 255, 255, 255))
        val edges = nodeType["edges"] as? Property.Object ?: Property.Object()
        val width = theme["width"] as? Property.Float ?: theme["width"] as? Property.Int ?: Property.Float(100f)
        val height = theme["height"] as? Property.Float ?: theme["height"] as? Property.Int ?: Property.Float(50f)
        val iconInt = theme["icon"] as? Property.Int ?: Property.Int(0)
        val newNode = configured<Node> {
            "name" to name
            "type" to nodeType.meta.group
            "color" to color
            "x" to position.x as Float
            "y" to position.y as Float
            "uid" to UUID.randomUUID()
            "width" to width
            "height" to height
            "edges" to Property.Object()
            "icon" to iconInt
        }

        // Process edges
        for ((edgeName, edgeProperty) in edges) {
            if (edgeProperty !is Property.Object) continue

            val edgeDirection = edgeProperty["direction"] as? Property.String ?: Property.String("input")
            val edgeType = edgeProperty["type"] as? Property.String ?: Property.String("exec")
            val edgeDescription = edgeProperty["description"] as? Property.String ?: Property.String("")
            val value = edgeProperty["value"] as? Property.Object ?: Property.Object()
            val edge = configured<Edge> {
                "name" to Property.String(edgeName)
                "direction" to edgeDirection
                "type" to edgeType
                "description" to edgeDescription
                "uid" to Property.UUID(UUID.randomUUID())
                "value" to value
            }

            (newNode["edges"] as Property.Object)[edgeName] = edge.properties
            workspace.addEdge(newNode, edge)
        }
        workspace.addNode(newNode)
        return newNode
    }


    /**
     * Creates a new workspace and sends the result back to the client.
     *
     * @param name The name of the new workspace.
     * @param description The description of the new workspace.
     * @param sendTo The UUID of the client to send the result to.
     */
    private fun createWorkspace(name: String, description: String, sendTo: UUID) {
        val workspace = Workspace.create(name, description)
        val response = new<WorkspaceCreateResponsePacket> {
            this.success = true
            this.workspaceUid = workspace.uid
        }
        //inject the schema into the workspace
        server.send(response, sendTo)
        logger.info { "Created new workspace for client $sendTo: ${workspace.workspaceName}" }
    }


    /**
     * Opens a workspace for a given user.
     *
     * @param workspaceId The ID of the workspace to open.
     * @param sendTo The UUID of the user to open the workspace for.
     */
    fun openWorkspace(workspaceId: UUID, sendTo: UUID) {
        val workspace = workspaces[workspaceId]
        if (workspace == null) {
            logger.warn { "Failed to open workspace: $workspaceId" }
            return
        }
        openedWorkspaces[sendTo] = workspaceId
        val user = User(sendTo, null, workspaceId)
        logger.debug { "Client '$sendTo' Opened workspace: $workspaceId, $user" }
        server.send(new<WorkspaceLoad> {
            this.workspace = workspace
        }, sendTo)
        notifyUsersOfWorkspace(sendTo)
        users[sendTo] = user
    }

    private fun notifyUsersOfWorkspace(sendTo: UUID) {
        val workspaceId = openedWorkspaces[sendTo] ?: return
        val users = users.filter { (_, user) ->
            user.workspaceUid == workspaceId && user.uid != sendTo
        }.map { (_, user) ->
            user
        }.toMutableList()
        if (users.isNotEmpty()) server.send(new<UserConnectedToWorkspace> {
            this.users.addAll(users)
        }, sendTo)


        // now we notify the other users that a new user has joined
        val user = this.users[sendTo] ?: return
        server.sendToAll(new<UserConnectedToWorkspace> {
            this.users.add(user)
        }, sendTo)

        val workspace = workspaces[workspaceId] ?: return

    }

    /**
     * Sends workspaces to the specified recipient.
     *
     * @param sendTo The recipient's UUID to send the workspaces to.
     */
    private fun sendWorkspaces(sendTo: UUID) {
//        refreshWorkspaces()
        val valuedWorkspaces = this.workspaces.mapValues { (_, workspace) ->
            Pair(workspace.workspaceName, workspace.description)
        }
        val workspaces = new<WorkspaceLibrary> {
            this.workspaces.putAll(valuedWorkspaces)
        }
        worker.endpoint.send(workspaces, sendTo)
        logger.debug { "Sent workspaces to: $sendTo, ${workspaces.workspaces}" }
    }

    /**
     * Broadcasts a node move packet to all connected clients except the specified sender.
     *
     * @param sender the UUID of the client to exclude from receiving the packet
     * @param nodeMovePacket the NodeMoved packet to broadcast
     */
    private fun broadCastNodeMove(sender: UUID, nodeMovePacket: NodeMoved) {

        //we get the workspace id by looking up the workspace id of the client that sent the packet.
        val senderWorkspaceId = openedWorkspaces[sender] ?: return
        val workspace = workspaces[senderWorkspaceId] ?: return

        //Only finds clients that have the same workspace opened.
        val clients = openedWorkspaces.filter { (_, workspaceId) ->
            workspaceId != senderWorkspaceId
        }.keys.toMutableList()

        //update the node position in the workspace
        workspace.getNode(nodeMovePacket.uid)?.let { node ->
            node.x = nodeMovePacket.x
            node.y = nodeMovePacket.y
        }

        //add the sender to the list of clients to exclude
        clients.add(sender)
        //send the packet to all clients that have the same workspace opened, except the sender.
        server.sendToAll(nodeMovePacket, *clients.toTypedArray())
        logger.debug { "Sent node move packet to all clients except sender, $sender" }
    }


}