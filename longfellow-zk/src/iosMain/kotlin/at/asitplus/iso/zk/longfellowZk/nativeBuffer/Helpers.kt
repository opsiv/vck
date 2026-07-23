package at.asitplus.iso.zk.longfellowZk.nativeBuffer

import kotlinx.cinterop.*
import platform.posix.size_tVar


@OptIn(ExperimentalForeignApi::class)
actual class NativeBufferPointers () {
    val byteArray = nativeHeap.alloc<CPointerVar<UByteVar>>()
    val byteArrayLength = nativeHeap.alloc<size_tVar>()
}
