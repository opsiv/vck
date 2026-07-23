package at.asitplus.iso.zk.longfellowZk.nativeBuffer

internal expect class NativeBufferDelegate() : NativeBuffer {
    override val pointers: NativeBufferPointers
    override fun snapshot(): ByteArray
    override fun free()
}