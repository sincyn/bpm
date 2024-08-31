package noderspace.common.workspace.graph

import noderspace.common.network.NetUtils
import noderspace.common.property.*
import java.util.*


data class Edge(override val properties: PropertyMap = Property.Object()) : PropertySupplier {

    val name: String by properties delegate { "Edge" }
    val uid: UUID by properties delegate { UUID.randomUUID() }
    var owner: UUID by properties delegate { NetUtils.DefaultUUID }
    val direction: String by properties delegate { "input" }
    val type: String by properties delegate { "exec" }
    val description: String by properties delegate { "" }
    val value: PropertyMap get() = properties.getTyped("value")

}