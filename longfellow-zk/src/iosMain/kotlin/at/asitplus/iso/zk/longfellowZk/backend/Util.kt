@file:Suppress("PropertyName", "FunctionName", "LocalVariableName")

package at.asitplus.iso.zk.longfellowZk.backend


import at.asitplus.iso.zk.longfellowZk.RequestedItem
import at.asitplus.iso.zk.longfellowZk.cinterop.RequestedAttribute
import kotlinx.cinterop.*
import kotlinx.cinterop.get
import platform.posix.memcpy


@OptIn(ExperimentalUnsignedTypes::class, ExperimentalForeignApi::class)
internal fun MemScope.convertToNative(
    requestedItems: List<RequestedItem>
): CValuesRef<RequestedAttribute>? {
    if (requestedItems.isEmpty()) return null

    val arr = allocArray<RequestedAttribute>(requestedItems.size)

    fun copyStringToNative(src: ByteArray, dstPtr: CPointer<UByteVar>): ULong {
        src.usePinned { pinned ->
            memcpy(dstPtr, pinned.addressOf(0), src.size.convert())
        }
        return src.size.toULong()
    }

    for (i in requestedItems.indices) {
        val src = requestedItems[i]
        val dst = arr[i]

        dst.namespace_len  = copyStringToNative(src.namespaceBytes, dst.namespace_id)
        dst.id_len         = copyStringToNative(src.elementIdentifierBytes, dst.id)
        dst.cbor_value_len = copyStringToNative(src.elementValueBytes, dst.cbor_value)
    }

    return arr
}

@OptIn(ExperimentalForeignApi::class)
internal fun <T : CVariable> Pinned<ByteArray>.asCPointer(): CPointer<T> = addressOf(0).reinterpret()

