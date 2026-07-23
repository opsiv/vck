package at.asitplus.iso.zk.longfellowZk.nativeBuffer

interface NativeBuffer {
    fun snapshot(): ByteArray
    fun free()
    val pointers: NativeBufferPointers
}