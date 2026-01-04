package at.asitplus.iso.zk.longfellowZk.nativeBuffer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value

internal actual class NativeBufferDelegate : NativeBuffer {

    actual override val pointers = NativeBufferPointers()

    @OptIn(ExperimentalForeignApi::class)
    actual override fun snapshot(): ByteArray {
        val buf = pointers.byteArray.value
        val len = pointers.byteArrayLength.value.toInt() // TODO check if it fits into Int
        return buf?.readBytes(len) ?: ByteArray(0)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual override fun free() {
        pointers.byteArray.value?.let{
            platform.posix.free(it)
        }
        nativeHeap.free(pointers.byteArray.rawPtr)
        nativeHeap.free(pointers.byteArrayLength.rawPtr)
    }

}
