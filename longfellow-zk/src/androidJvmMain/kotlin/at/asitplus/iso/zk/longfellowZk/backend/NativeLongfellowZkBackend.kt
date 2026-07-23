package at.asitplus.iso.zk.longfellowZk.backend

import at.asitplus.KmmResult
import at.asitplus.iso.zk.longfellowZk.RequestedItem
import at.asitplus.iso.zk.longfellowZk.ZkSpecHandle
import at.asitplus.iso.zk.longfellowZk.nativeBuffer.ScopedNativeBuffer
import at.asitplus.iso.zk.longfellowZk.resultCode.CircuitResultCode
import at.asitplus.iso.zk.longfellowZk.resultCode.NativeResult
import at.asitplus.iso.zk.longfellowZk.resultCode.NativeResultException
import at.asitplus.iso.zk.longfellowZk.resultCode.ProverResultCode
import at.asitplus.iso.zk.longfellowZk.resultCode.VerifierResultCode
import com.sun.jna.Native

actual object NativeLongfellowZkBackend : LongfellowZkBackend by JnaLongfellowZkBackend

object JnaLongfellowZkBackend: LongfellowZkBackend {
    private const val UNINIT_WARNING = "JNA longfellow library not initialized"
    private var initialized = false
    private lateinit var delegate: IJnaLibrary

    override fun findZkSpec(systemName: String, circuitHash: String): KmmResult<ZkSpecHandle> {
        require(initialized) { UNINIT_WARNING }
        return delegate.find_zk_spec(systemName, circuitHash)
            .toKmmResultIfNotNull()
    }
    override fun generateCircuit(zkSpec: ZkSpecHandle): KmmResult<ByteArray> {
        require(initialized) { UNINIT_WARNING }
        val result = ScopedNativeBuffer { pointers ->
            delegate.generate_circuit(
                zkSpec,
                pointers.byteArray,
                pointers.byteArrayLength,
            )
        }
        return NativeResult.fromInt<CircuitResultCode, ByteArray>(result)
    }

    override fun generateProof(
        circuit: ByteArray,
        deviceResponse: ByteArray,
        publicKeyX: String,
        publicKeyY: String,
        transcript: ByteArray,
        timestamp: String,
        requestedItems: List<RequestedItem>,
        zkSpec: ZkSpecHandle,
    ): KmmResult<ByteArray> {
        require(initialized) { UNINIT_WARNING }
        val attributeStructs = requestedItems.toStructArray()
        val result =  ScopedNativeBuffer { pointers ->
            delegate.run_mdoc_prover(
                bcp = circuit, bcsz = circuit.size.toLong(),
                mdoc = deviceResponse, mdoc_len = deviceResponse.size.toLong(),
                pkx = publicKeyX,
                pky = publicKeyY,
                transcript = transcript, tr_len = transcript.size.toLong(),
                attrs = attributeStructs, attrs_len = attributeStructs.size.toLong(),
                now = timestamp,
                prf = pointers.byteArray, proof_len = pointers.byteArrayLength,
                zkSpec = zkSpec
            )
        }
        return NativeResult.fromInt<ProverResultCode, ByteArray>(result)
    }

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
        val attributeStructs = requestedItems.toStructArray()
        val intCode = delegate.run_mdoc_verifier(
            bcp = circuit, bcsz = circuit.size.toLong(),
            pkx = publicKeyX,
            pky = publicKeyY,
            transcript = transcript, tr_len = transcript.size.toLong(),
            attrs = attributeStructs, attrs_len = attributeStructs.size.toLong(),
            now = timestamp,
            prf = proof, proof_len = proof.size.toLong(),
            doc_type = docType,
            zkSpec = zkSpec
        )
        val resultCode = VerifierResultCode.fromInt(intCode)
        return when {
            resultCode.isSuccess -> KmmResult(true)
            resultCode in VerifierResultCode.invalidInputs -> KmmResult(false)
            else ->  KmmResult.failure(NativeResultException(resultCode))
        }
    }

    override fun initialize(): KmmResult<Unit> {
        if (!initialized) {
            try {
                delegate = Native.load(
                    /* name = */ getJNaLibraryPath(),
                    /* interfaceClass = */ IJnaLibrary::class.java
                )
                initialized = true
            } catch (e: Exception) { return KmmResult.failure(e) }
        }
        return KmmResult.success(Unit)
    }

    private fun getJNaLibraryPath(): String {
        val defaultName = "longfellow_mdoc"
        val isAndroid = System.getProperty("java.vendor")?.lowercase()?.contains("android") == true
        if (isAndroid) return defaultName

        // TODO: Loading the class with [getJNaLibraryPath] this might break in actual projects. This function should be
        //  replaced with a function that reliably gets the library on JVM targets
        val os = System.getProperty("os.name").lowercase().replace("\\s+".toRegex(), "")
        val arch = System.getProperty("os.arch").lowercase().replace("\\s+".toRegex(), "")
        val suffix = when {
            os.contains("mac") || os.contains("darwin") -> "dylib"
            os.contains("win") -> "dll"
            else -> "so"
        }

        return "/native/${os}-${arch}/${defaultName}.${suffix}"
    }

}

