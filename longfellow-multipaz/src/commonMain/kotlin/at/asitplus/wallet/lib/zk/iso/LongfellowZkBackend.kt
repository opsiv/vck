package at.asitplus.wallet.lib.zk.iso

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.iso.SessionTranscript
import at.asitplus.iso.ZkDocument
import at.asitplus.iso.ZkSystemSpec
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.openid.truncateToSeconds
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.SubjectCredentialStore.StoreEntry
import at.asitplus.wallet.lib.cbor.CoseHeaderNone
import at.asitplus.wallet.lib.cbor.SignCoseDetached
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.multipaz.mdoc.zkp.ZkSystemSpec as MultipazZkSystemSpec
import org.multipaz.mdoc.zkp.longfellow.LongfellowZkSystem
import kotlin.ByteArray
import kotlin.time.Clock

class LongfellowZkBackend : IsoMdocZkBackend {

    private class InitializedState(val backend: LongfellowZkSystem) {
        val zkSystemsMap: Map<ZkSystemSpec, MultipazZkSystemSpec> =
            backend.systemSpecs.associateBy { it.toZkSystemSpec() }
        val zkSystemSpecs: List<ZkSystemSpec> =
            zkSystemsMap.keys.toList()
    }

    private var state: InitializedState? = null

    private val currentState: InitializedState
        get() = checkNotNull(state) { "LongfellowZkBackend is not initialized. Call initialize() first." }

    override val zkSystemSpecs: List<ZkSystemSpec>
        get() = currentState.zkSystemSpecs

    override val system: String
        get() = currentState.backend.name

    override val paramSerializers: Map<String, KSerializer<*>> = mapOf(
        "version" to Long.serializer(),
        "circuit_hash" to String.serializer(),
        "num_attributes" to Long.serializer(),
        "block_enc_hash" to Long.serializer(),
        "block_enc_sig" to Long.serializer(),
    )

    override fun supports(candidate: ZkSystemSpec): Boolean = zkSystemSpecs.any { supportedSpec ->
        supportedSpec.system == candidate.system &&
                candidate.params["circuit_hash"] != null &&
                candidate.params["circuit_hash"] == supportedSpec.params["circuit_hash"]
    }

    private fun chooseZkSystemSpec(
        credential: StoreEntry.Iso,
        requestedClaims: Collection<NormalizedJsonPath>,
        zkSystemSpecs: List<ZkSystemSpec>,
    ): MultipazZkSystemSpec? {
        val multipazRequestedClaims = requestedClaims.map {
            it.toMdocRequestedClaim(credential.schemeIdentifier)
        }
        val multipazZkSystemSpecs = zkSystemSpecs
            .filter { supports(it) }
            .map { it.toMultipazZkSystemSpec() }

        return currentState.backend.getMatchingSystemSpec(multipazZkSystemSpecs, multipazRequestedClaims)?.let { spec ->
            val id = multipazZkSystemSpecs.first { it.params["circuit_hash"] == spec.params["circuit_hash"] }.id
            spec.copyWithParameters(id = id)
        }
    }

    override suspend fun generate(
        request: PresentationRequestParameters,
        credential: StoreEntry.Iso,
        requestedClaims: Collection<NormalizedJsonPath>,
        requestedZkSystemSpecs: List<ZkSystemSpec>,
        keyMaterial: KeyMaterial
    ): KmmResult<IsoMdocZkProof> = catching {
        val sessionTranscript = requireNotNull(request.calcIsoSessionTranscript()) {
            "calcIsoSessionTranscript not implemented"
        }
        val selectedMultipazZkSystemSpec = requireNotNull(
            chooseZkSystemSpec(credential, requestedClaims, requestedZkSystemSpecs)
        ) { "No matching ZK system spec found" }
        val selectedZkSystemSpec = selectedMultipazZkSystemSpec.toZkSystemSpec()

        val signDeviceAuthDetached = SignCoseDetached<ByteArray>(
            keyMaterial = keyMaterial,
            protectedHeaderModifier = CoseHeaderNone(),
            unprotectedHeaderModifier = CoseHeaderNone()
        )
        val plainMultipazDocument = credential
            .discloseRequestedClaims(requestedClaims, sessionTranscript, signDeviceAuthDetached)
            .toMultipazDocument()

        val zkDocument = currentState.backend.generateProof(
            zkSystemSpec = selectedMultipazZkSystemSpec,
            document = plainMultipazDocument,
            sessionTranscript = sessionTranscript.toMultipazSessionTranscript(),
            timestamp = Clock.System.now().truncateToSeconds(),
        ).toZkDocument()

        IsoMdocZkProof(
            zkDocument = zkDocument,
            verifyFn = createVerifyFn(selectedZkSystemSpec, sessionTranscript, zkDocument),
        )
    }

    override fun load(
        zkDocument: ZkDocument,
        sessionTranscript: SessionTranscript,
        zkSystemSpec: ZkSystemSpec
    ): KmmResult<IsoMdocZkProof> = catching {
        require(supports(zkSystemSpec)) {
            "LongfellowZkBackend does not support spec: ${zkSystemSpec.id}"
        }
        IsoMdocZkProof(
            zkDocument = zkDocument,
            verifyFn = createVerifyFn(zkSystemSpec, sessionTranscript, zkDocument),
        )
    }

    private fun createVerifyFn(
        zkSystemSpec: ZkSystemSpec,
        sessionTranscript: SessionTranscript,
        zkDocument: ZkDocument
    ): suspend () -> KmmResult<Unit> = {
        catching {
            currentState.backend.verifyProof(
                zkDocument = zkDocument.toMultipazZkDocument(),
                zkSystemSpec = zkSystemSpec.toMultipazZkSystemSpec(),
                sessionTranscript = sessionTranscript.toMultipazSessionTranscript(),
            )
        }
    }

    override suspend fun initialize(): KmmResult<Unit> = catching {
        state = InitializedState(
            backend = LongfellowZkSystem().also { it.addDefaultCircuits() }
        )
    }
}