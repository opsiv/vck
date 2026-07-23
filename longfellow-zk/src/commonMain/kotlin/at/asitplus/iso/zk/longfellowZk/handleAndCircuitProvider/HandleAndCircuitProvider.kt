package at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider

import at.asitplus.iso.zk.longfellowZk.ZkParams
import at.asitplus.iso.zk.longfellowZk.ZkSpecHandle

interface HandleAndCircuitProvider {
    fun getHandle(params: ZkParams): ZkSpecHandle
    fun getCircuit(params: ZkParams): ByteArray
}