@file:Suppress("PropertyName")

package at.asitplus.iso.zk.longfellowZk.backend

import at.asitplus.iso.zk.longfellowZk.RequestedItem
import com.sun.jna.Structure

@Structure.FieldOrder("namespace_id", "id", "cbor_value", "namespace_len", "id_len", "cbor_value_len")
open class RequestedAttributeStruct : Structure() {
    @JvmField var namespace_id = ByteArray(RequestedItem.NAMESPACE_SIZE)
    @JvmField var id = ByteArray(RequestedItem.ELEMENT_IDENTIFIER_SIZE)
    @JvmField var cbor_value = ByteArray(RequestedItem.CBOR_ELEMENT_VALUE_SIZE)
    @JvmField var namespace_len: Long = 0
    @JvmField var id_len: Long = 0
    @JvmField var cbor_value_len: Long = 0
}

@Suppress("UNCHECKED_CAST")
internal fun List<RequestedItem>.toStructArray(): Array<RequestedAttributeStruct> {
    val structs = RequestedAttributeStruct().toArray(size) as Array<RequestedAttributeStruct>
    val structIterator = structs.iterator()
    forEach { item ->
        require(item.isValid()) {"RequestedItem is invalid!"}
        val struct = structIterator.next()

        item.namespaceBytes.copyInto(struct.namespace_id)
        struct.namespace_len = item.namespaceBytes.size.toLong()
        item.elementIdentifierBytes.copyInto(struct.id)
        struct.id_len = item.elementIdentifierBytes.size.toLong()
        item.elementValueBytes.copyInto(struct.cbor_value)
        struct.cbor_value_len = item.elementValueBytes.size.toLong()

        struct.write()

    }
    return structs
}