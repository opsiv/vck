package at.asitplus.iso.zk.longfellowZk.nativeBuffer

import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
actual class NativeBufferPointers {
    val byteArray: PointerByReference = PointerByReference()
    val byteArrayLength = LongByReference()
}