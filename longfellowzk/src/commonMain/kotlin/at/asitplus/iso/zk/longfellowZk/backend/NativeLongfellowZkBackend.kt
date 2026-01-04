package at.asitplus.iso.zk.longfellowZk.backend

import at.asitplus.KmmResult
import at.asitplus.iso.zk.longfellowZk.RequestedItem
import at.asitplus.iso.zk.longfellowZk.ZkSpecHandle

expect object NativeLongfellowZkBackend: LongfellowZkBackend {
    override fun initialize(): KmmResult<Unit>
    override fun generateCircuit(zkSpec: ZkSpecHandle): KmmResult<ByteArray>
    override fun generateProof(
        circuit: ByteArray,
        deviceResponse: ByteArray,
        publicKeyX: String,
        publicKeyY: String,
        transcript: ByteArray,
        timestamp: String,
        requestedItems: List<RequestedItem>,
        zkSpec: ZkSpecHandle
    ): KmmResult<ByteArray>

    override fun verifyProof(
        circuit: ByteArray,
        publicKeyX: String,
        publicKeyY: String,
        transcript: ByteArray,
        requestedItems: List<RequestedItem>,
        timestamp: String,
        proof: ByteArray,
        docType: String,
        zkSpec: ZkSpecHandle
    ): KmmResult<Boolean>

    override fun findZkSpec(
        systemName: String,
        circuitHash: String
    ): KmmResult<ZkSpecHandle>
}


internal inline fun <T> T?.toKmmResultIfNotNull(
    exception: () -> Throwable = { NoSuchElementException("Value was null") }
): KmmResult<T> =
    if (this != null) KmmResult.success(this) else KmmResult.failure(exception())

