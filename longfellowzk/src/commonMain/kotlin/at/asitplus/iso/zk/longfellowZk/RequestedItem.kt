package at.asitplus.iso.zk.longfellowZk

import at.asitplus.iso.ZkSignedItemSerializer
import io.github.aakira.napier.Napier


class RequestedItem(
    namespace: String,
    elementIdentifier: String,
    elementValue: Any
) {
    val namespaceBytes: ByteArray = namespace.encodeToByteArray()
    val elementIdentifierBytes: ByteArray = elementIdentifier.encodeToByteArray()
    val elementValueBytes: ByteArray = ZkSignedItemSerializer.Companion.serializeElementValue(
        namespace,
        elementValue,
        elementIdentifier
    )

    fun isValid(): Boolean {
        return namespaceBytes.size <= NAMESPACE_SIZE
                && elementIdentifierBytes.size <= ELEMENT_IDENTIFIER_SIZE
                && elementValueBytes.size <= CBOR_ELEMENT_VALUE_SIZE
    }

    init {
        if (namespaceBytes.size > NAMESPACE_SIZE) {
            Napier.d("Namespace '$namespace' length ${namespaceBytes.size} exceeds max $NAMESPACE_SIZE")
        }
        if (elementIdentifierBytes.size > ELEMENT_IDENTIFIER_SIZE) {
            Napier.d("Element identifier '$elementIdentifier' length ${elementIdentifierBytes.size} exceeds max $ELEMENT_IDENTIFIER_SIZE")
        }
        if (elementValueBytes.size > CBOR_ELEMENT_VALUE_SIZE) {
            Napier.d("CBOR value for '$elementIdentifier' length ${elementValueBytes.size} exceeds max $CBOR_ELEMENT_VALUE_SIZE")
        }
    }


    companion object {
        // LongfellowZk specific values
        const val NAMESPACE_SIZE = 64
        const val ELEMENT_IDENTIFIER_SIZE = 32
        const val CBOR_ELEMENT_VALUE_SIZE = 64

    }
}