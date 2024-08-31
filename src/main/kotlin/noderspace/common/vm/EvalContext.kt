package noderspace.common.vm

import kotlinx.coroutines.*
import noderspace.common.codegen.LuaCodeGenerator
import noderspace.common.logging.KotlinLogging
import noderspace.common.workspace.Workspace
import party.iroiro.luajava.*
import party.iroiro.luajava.luajit.LuaJit
import party.iroiro.luajava.value.LuaValue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EvalContext {

    private val logger = KotlinLogging.logger {}
    private lateinit var lua: Lua
    //    private val functionGroups: ConcurrentHashMap<String, MutableMap<String, LuaValue>> = ConcurrentHashMap()
    private val workspaceFunctionGroups: ConcurrentHashMap<UUID, ConcurrentHashMap<String, MutableMap<String, LuaValue>>> = ConcurrentHashMap()

    init {
        initializeLuaJit()
    }

    private fun initializeLuaJit() {
        try {
            lua = LuaJit()
            initializeLuaState()
        } catch (e: Exception) {
            println("Error loading LuaJit: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun initializeLuaState() {
        lua.openLibraries()
        lua.setExternalLoader(ClassPathLoader())
        defineLuaFunctions()
    }

    private fun defineLuaFunctions() {
        lua.run(
            """
            local Endpoint = java.import("noderspace.common.network.Endpoint").INSTANCE
            local NotifyPacket = java.import("noderspace.common.workspace.packets.NotifyMessage")
            function info(message)
                local endpoint = Endpoint.get()
                local notifyPacket = NotifyPacket(message)
                endpoint:sendToAll(notifyPacket)
            end
        """.trimIndent()
        )
    }

    fun eval(workspace: Workspace) {
        val functionGroups = workspaceFunctionGroups.computeIfAbsent(workspace.uid) { ConcurrentHashMap() }
        functionGroups.clear()
        val compiledSource = LuaCodeGenerator.generateLuaScript(workspace)
        logger.debug { "Compiled Lua script: $compiledSource" }
        val result = lua.eval(compiledSource)[0]
        if (result.type() == Lua.LuaType.TABLE) {
            for (groupKey in result.keys) {
                val group = result.get(groupKey)
                if (group?.type() == Lua.LuaType.TABLE) {
                    val groupName = groupKey.toString()
                    functionGroups[groupName] = mutableMapOf()
                    for (functionKey in group.keys) {
                        val function = group.get(functionKey)
                        if (function?.type() == Lua.LuaType.FUNCTION) {
                            val functionName = functionKey.toString()
                            functionGroups[groupName]!![functionName] = function
                            println("Function $groupName.$functionName defined")
                        }
                    }
                }
            }
        }
    }


    data class Success(val successes: List<Any?>)

    data class Failure(val error: String, val failedFunctions: List<String>)

    data class Result(val successes: Success? = null, val failure: Failure? = null) {

        val isSuccess: Boolean
            get() = successes != null

        val hasResults: Boolean
            get() = successes != null && successes.successes.isNotEmpty()

        val results: List<Any?>
            get() = successes?.successes ?: emptyList()

        val isFailure: Boolean
            get() = failure != null
    }

    fun callFunction(workspace: Workspace, groupName: String, vararg args: Any?): Result {
        val functionGroups = workspaceFunctionGroups[workspace.uid] ?: return Result(
            null,
            Failure("Workspace not found", emptyList())
        )
        val group = functionGroups[groupName] ?: return Result(
            null,
            Failure("Group $groupName not found", emptyList())
        )
        val successes = mutableListOf<Any?>()
        for ((functionName, function) in group) {
            try {
                val result = function.call(*args)
                successes.addAll(result.map { value -> value.toJavaObject() })
            } catch (e: LuaException) {
                logger.warn { "Failed to call function $groupName.$functionName: ${e.message}" }
                return Result(
                    null,
                    Failure(e.message ?: "Unknown error", listOf("$groupName.$functionName"))
                )
            }
        }
        return Result(Success(successes), null)
    }

    fun callAllFunctions(workspace: Workspace, vararg args: Any?): List<Any?> {
        val functionGroups = workspaceFunctionGroups[workspace.uid] ?: return emptyList()
        return functionGroups.values.flatMap { group ->
            group.values.flatMap { function ->
                function.call(*args).map { value -> value.toJavaObject() }
            }
        }
    }

    fun close() {
        lua.close()
    }
}