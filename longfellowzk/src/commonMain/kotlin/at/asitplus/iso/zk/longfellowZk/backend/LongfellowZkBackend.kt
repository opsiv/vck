package at.asitplus.iso.zk.longfellowZk.backend

import at.asitplus.KmmResult
import at.asitplus.iso.zk.longfellowZk.RequestedItem
import at.asitplus.iso.zk.longfellowZk.ZkSpecHandle


/**
 * Interacts with the native LongfellowZk backend.
 * The requestedItems parameter is not directly compatible with the native library, because KMP does not allow for an
 * adequate type-alias. Any native implementation needs to convert requestedItems into the native representation.
 * All other parameters should be used as is.
 */
interface LongfellowZkBackend {

    fun initialize(): KmmResult<Unit>
    fun generateCircuit(zkSpec: ZkSpecHandle): KmmResult<ByteArray>

    fun generateProof(
        circuit: ByteArray,
        deviceResponse: ByteArray,
        publicKeyX: String,
        publicKeyY: String,
        transcript: ByteArray,
        timestamp: String,
        requestedItems: List<RequestedItem>,
        zkSpec: ZkSpecHandle
    ): KmmResult<ByteArray>

    fun verifyProof(
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

    fun findZkSpec(systemName: String, circuitHash: String): KmmResult<ZkSpecHandle>
}




