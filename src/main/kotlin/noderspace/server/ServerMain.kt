package noderspace.server

import noderspace.common.logging.KotlinLogging
import noderspace.server.environment.ServerRuntime
import noderspace.common.managers.Heartbearts
import noderspace.common.network.Server
import noderspace.common.managers.Schemas
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    //Get the schemas path from args or use the default
    val schemasPath = if (args.isNotEmpty()) {
        Path.of(args[0])
    } else {
        Path.of("src/main/resources/assets/schemas")
    }
    val server = Server()
        .install<Heartbearts>()
        .install<ServerRuntime>()
//        .install<Schemas>(Path.of("C:\\Users\\jraynor\\IdeaProjects\\bp\\graph-common\\src\\main\\resources\\assets\\schemas"))
        .install<Schemas>(schemasPath)
        .start()
    while (server.isRunning()) {
//        Thread.sleep(30000)
//        val threads: Set<Thread> = Thread.getAllStackTraces().keys
//        System.out.printf("%-15s \t %-15s \t %-15s \t %s\n", "Name", "State", "Priority", "isDaemon")
//        for (t in threads) {
//            System.out.printf("%-15s \t %-15s \t %-15d \t %s\n", t.name, t.state, t.priority, t.isDaemon)
//        }

        //TODO: take command line input
    }
    println("Server stopped")
}