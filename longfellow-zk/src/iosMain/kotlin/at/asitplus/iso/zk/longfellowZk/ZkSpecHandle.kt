package at.asitplus.iso.zk.longfellowZk


import at.asitplus.iso.zk.longfellowZk.cinterop.ZkSpecStruct
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual class ZkSpecHandle private constructor(
    val ptr: CPointer<ZkSpecStruct>
) {
    companion object {
        internal fun toZkSpecHandle(ptr: CPointer<ZkSpecStruct>?): ZkSpecHandle? {
          return ptr?.let { return ZkSpecHandle(it) }
        }
    }
}
