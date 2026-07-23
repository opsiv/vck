package at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider

import at.asitplus.iso.zk.longfellowZk.ZkParams
import at.asitplus.iso.zk.longfellowZk.ZkSpecHandle
import at.asitplus.iso.zk.longfellowZk.backend.LongfellowZkBackend


class BasicHandleAndCircuitProvider(
    private val backend: LongfellowZkBackend
) : HandleAndCircuitProvider {
    override fun getHandle(params: ZkParams): ZkSpecHandle =
        backend.findZkSpec(params.systemName, params.circuitId).getOrThrow()

    override fun getCircuit(params: ZkParams): ByteArray =
        backend.generateCircuit(getHandle(params)).getOrThrow()
}