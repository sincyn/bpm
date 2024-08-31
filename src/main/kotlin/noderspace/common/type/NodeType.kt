package noderspace.common.type

import noderspace.common.property.*
import noderspace.common.type.*


data class NodeType(
    val meta: NodeTypeMeta,
    val properties: PropertyMap,
) : Property.Object(properties.get()) {

    fun isAbstract(): Boolean = meta.group == "Base"
}
