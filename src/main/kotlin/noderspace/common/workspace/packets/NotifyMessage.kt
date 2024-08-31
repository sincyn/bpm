package noderspace.common.workspace.packets

import noderspace.common.memory.Buffer
import noderspace.common.packets.Packet


data class NotifyMessage(
    var icon: Int = 0xf05a,
    var color: String = "#ecabec",
    var message: String = "",
    var lifetime: Float = 5.0f, //5 seconds
    var type: NotificationType = NotificationType.INFO,
    var header: String = if (type == NotificationType.INFO) "Info" else if (type == NotificationType.SUCCESS) "Success" else if (type == NotificationType.WARNING) "Warning" else "Error"
) : Packet {
    var time: Float = 0f

    enum class NotificationType {
        INFO, SUCCESS, WARNING, ERROR
    }

    override fun serialize(buffer: Buffer) {
        buffer.writeInt(icon)
        buffer.writeString(color)
        buffer.writeString(message)
        buffer.writeFloat(lifetime)
        buffer.writeInt(type.ordinal)
        buffer.writeString(header)
    }

    override fun deserialize(buffer: Buffer) {
        icon = buffer.readInt()
        color = buffer.readString()
        message = buffer.readString()
        lifetime = buffer.readFloat()
        type = NotificationType.entries.toTypedArray()[buffer.readInt()]
        header = buffer.readString()
    }
}