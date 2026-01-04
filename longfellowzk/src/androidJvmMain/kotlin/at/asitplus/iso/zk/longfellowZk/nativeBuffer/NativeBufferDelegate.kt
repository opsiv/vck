package at.asitplus.iso.zk.longfellowZk.nativeBuffer

import com.sun.jna.Native
import com.sun.jna.Pointer

internal actual class NativeBufferDelegate : NativeBuffer {

    private fun validLength(len: Long?) =
        len?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    private fun validPointer(ptr: Pointer?) =
        ptr?.takeUnless { it == Pointer.NULL }

    actual override val pointers = NativeBufferPointers()

    actual override fun snapshot(): ByteArray =
        validLength(pointers.byteArrayLength.value)?.let { len ->
            validPointer(pointers.byteArray.value)?.let { ptr ->
                ByteArray(len).apply { ptr.read(0, this, 0, len) }
            }
        } ?: ByteArray(0)

    actual override fun free() {
        Native.free(Pointer.nativeValue(pointers.byteArray.value))
        pointers.byteArray.value = null
        pointers.byteArrayLength.value = 0
    }
}