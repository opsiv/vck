package at.asitplus.iso.zk.longfellowZk.backend

import at.asitplus.KmmResult
import at.asitplus.iso.zk.longfellowZk.RequestedItem
import at.asitplus.iso.zk.longfellowZk.ZkSpecHandle
import at.asitplus.iso.zk.longfellowZk.cinterop.RequestedAttribute
import at.asitplus.iso.zk.longfellowZk.cinterop.find_zk_spec
import at.asitplus.iso.zk.longfellowZk.cinterop.generate_circuit
import at.asitplus.iso.zk.longfellowZk.cinterop.run_mdoc_prover
import at.asitplus.iso.zk.longfellowZk.cinterop.run_mdoc_verifier
import at.asitplus.iso.zk.longfellowZk.nativeBuffer.ScopedNativeBuffer
import at.asitplus.iso.zk.longfellowZk.resultCode.CircuitResultCode
import at.asitplus.iso.zk.longfellowZk.resultCode.NativeResult
import at.asitplus.iso.zk.longfellowZk.resultCode.NativeResultException
import at.asitplus.iso.zk.longfellowZk.resultCode.ProverResultCode
import at.asitplus.iso.zk.longfellowZk.resultCode.VerifierResultCode
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned

actual object NativeLongfellowZkBackend : LongfellowZkBackend by CinteropLongfellowZkBackend
object CinteropLongfellowZkBackend: LongfellowZkBackend {
    private var initialized = false
    private const val UNINIT_WARNING = "Cinterop library not initialized"

    @OptIn(ExperimentalForeignApi::class)
    override fun findZkSpec(systemName: String, circuitHash: String): KmmResult<ZkSpecHandle> {
        require(initialized) { UNINIT_WARNING }
        val zkSpecPtr = find_zk_spec(systemName, circuitHash)
        return ZkSpecHandle.toZkSpecHandle(zkSpecPtr).toKmmResultIfNotNull()
    }

    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalForeignApi::class)
    override fun generateCircuit(zkSpec: ZkSpecHandle): KmmResult<ByteArray> {
        require(initialized) { UNINIT_WARNING }
        val result = ScopedNativeBuffer { pointers ->
            generate_circuit(
                zk_spec_version = zkSpec.ptr,
                cb = pointers.byteArray.ptr,
                clen = pointers.byteArrayLength.ptr
            ).toInt()
        }
        return NativeResult.fromInt<CircuitResultCode, ByteArray>(result)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun generateProof(
        circuit: ByteArray,
        deviceResponse: ByteArray,
        publicKeyX: String,
        publicKeyY: String,
        transcript: ByteArray,
        timestamp: String,
        requestedItems: List<RequestedItem>,
        zkSpec: ZkSpecHandle
    ): KmmResult<ByteArray> {
        require(initialized) { UNINIT_WARNING }
        transcript.usePinned { pTranscript ->
            circuit.usePinned { pCircuit ->
                deviceResponse.usePinned  { pDeviceResponse ->
                    val result = ScopedNativeBuffer { pointers ->
                        memScoped {
                            val attrs: CValuesRef<RequestedAttribute>? = convertToNative(requestedItems)
                            run_mdoc_prover(
                                bcp = pCircuit.asCPointer(), bcsz = pCircuit.get().size.toULong(),
                                mdoc = pDeviceResponse.asCPointer(), mdoc_len = pDeviceResponse.get().size.toULong(),
                                pkx = publicKeyX, pky = publicKeyY,
                                transcript = pTranscript.asCPointer(), pTranscript.get().size.toULong(),
                                attrs = attrs, attrs_len =  requestedItems.size.toULong(),
                                now = timestamp,
                                prf = pointers.byteArray.ptr, proof_len = pointers.byteArrayLength.ptr,
                                zk_spec_version = zkSpec.ptr
                            ).toInt()
                        }
                    }
                    return NativeResult.fromInt<ProverResultCode, ByteArray>(result)
                }
            }
        }
    }


    @OptIn(ExperimentalForeignApi::class)
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
    ): KmmResult<Boolean> {
        require(initialized) { UNINIT_WARNING }
        transcript.usePinned { pTranscript ->
            circuit.usePinned { pCircuit ->
                proof.usePinned { pProof ->
                    memScoped {
                        val attrs: CValuesRef<RequestedAttribute>? = convertToNative(requestedItems)
                        val intCode = run_mdoc_verifier(
                            bcp = pCircuit.asCPointer(), bcsz = pCircuit.get().size.toULong(),
                            pkx = publicKeyX,
                            pky = publicKeyY,
                            transcript = pTranscript.asCPointer(), pTranscript.get().size.toULong(),
                            attrs = attrs, requestedItems.size.toULong(),
                            now = timestamp,
                            zkproof = pProof.asCPointer(), proof_len = pProof.get().size.toULong(),
                            docType = docType,
                            zk_spec_version = zkSpec.ptr
                        ).toInt()
                        val resultCode = VerifierResultCode.fromInt(intCode)
                        return when {
                            resultCode.isSuccess -> KmmResult(true)
                            resultCode in VerifierResultCode.invalidInputs -> KmmResult(false)
                            else ->  KmmResult.failure(NativeResultException(resultCode))
                        }
                    }
                }
            }
        }
    }

    override fun initialize(): KmmResult<Unit> {
        // Should always succeed, because library linking is configured at build time
        initialized = true
        return KmmResult.success(Unit)
    }
}

