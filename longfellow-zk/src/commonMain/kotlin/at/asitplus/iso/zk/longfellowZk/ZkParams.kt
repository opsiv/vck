package at.asitplus.iso.zk.longfellowZk

import at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider.HandleAndCircuitProvider


data class ZkParams(
    val systemName: String,
    val circuitId: String,
    private val provider: HandleAndCircuitProvider
) {
    val circuit: ByteArray by lazy { provider.getCircuit(this) }
    val handle: ZkSpecHandle by lazy { provider.getHandle(this) }
}

