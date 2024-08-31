package noderspace.server.environment

import noderspace.common.network.Endpoint
import noderspace.common.network.Network.new
import noderspace.common.workspace.packets.NotifyMessage

object Notify {

    @JvmStatic
    fun info(msg: String) {
        Endpoint.get().sendToAll(
            new<NotifyMessage> {
                icon = 0x0021
                message = msg
                color = "#4287f5"
                lifetime = 2.5f
                type = NotifyMessage.NotificationType.INFO
            })
    }
}