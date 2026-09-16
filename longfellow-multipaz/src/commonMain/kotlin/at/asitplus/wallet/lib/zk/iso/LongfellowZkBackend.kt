package at.asitplus.wallet.lib.zk.iso

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.iso.SessionTranscript
import at.asitplus.iso.ZkDocument
import at.asitplus.iso.ZkSystemSpec
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.jsonpath.core.NormalizedJsonPathSegment
import at.asitplus.openid.truncateToSeconds
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.SubjectCredentialStore.StoreEntry
import at.asitplus.wallet.lib.cbor.CoseHeaderNone
import at.asitplus.wallet.lib.cbor.SignCoseDetached
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.multipaz.mdoc.zkp.ZkSystemParamValue
import org.multipaz.mdoc.zkp.ZkSystemSpec as MultipazZkSystemSpec
import org.multipaz.mdoc.zkp.longfellow.LongfellowZkSystem
import org.multipaz.request.MdocRequestedClaim
import kotlin.ByteArray
import kotlin.time.Clock
class LongfellowZkBackend : IsoMdocZkBackend {
    private lateinit var backend: LongfellowZkSystem
    private val zkSystemsMap: Map<ZkSystemSpec, MultipazZkSystemSpec> get() =
        backend.systemSpecs.associateBy { it.toZkSystemSpec() }

    private fun MultipazZkSystemSpec.toZkSystemSpec() = ZkSystemSpec(
        id = id,
        system = system,
        params = params.entries.associate { (key, paramValue) ->
            key to when (paramValue) {
                is ZkSystemParamValue.BooleanValue -> paramValue.value
                is ZkSystemParamValue.StringValue -> paramValue.value
                is ZkSystemParamValue.DoubleValue -> paramValue.value
                is ZkSystemParamValue.LongValue -> paramValue.value
            }
        }
    )

    private fun ZkSystemSpec.toMultipazZkSystemSpec(): MultipazZkSystemSpec = MultipazZkSystemSpec(id, system).also { spec ->
        params.forEach { (key, value) ->
            when (value) {
                is String -> spec.addParam(key, value)
                is Int -> spec.addParam(key, value.toLong())
                is Long -> spec.addParam(key, value)
                is Double -> spec.addParam(key, value)
                is Boolean -> spec.addParam(key, value)
                else -> throw IllegalArgumentException(
                    "Cannot convert to Multipaz ZkSystemSpec due to unsupported parameter value type: " +
                            "${value::class.simpleName ?: value::class} for key '$key'"
                )
            }
        }
    }


    private fun NormalizedJsonPath.toMdocRequestedClaim(
        docType: String,
    ): MdocRequestedClaim {
        require(segments.size == 2 && segments.all { it is NormalizedJsonPathSegment.NameSegment }) {
            "Expected an mdoc claim path with a namespace and data element: $this"
        }

        val (namespaceName, dataElementName) = segments
            .map { (it as NormalizedJsonPathSegment.NameSegment).memberName }

        return MdocRequestedClaim(
            docType = docType,
            namespaceName = namespaceName,
            dataElementName = dataElementName,
            intentToRetain = false, // TODO: Consider using the actual value instead of a place holder "false"
        )
    }

    override val zkSystemSpecs:  List<ZkSystemSpec> get() = zkSystemsMap.keys.toList()

    override val system: String get() = backend.name

    override val paramSerializers: Map<String, KSerializer<*>> = mapOf(
        "version" to Long.serializer(),
        "circuit_hash" to String.serializer(),
        "num_attributes" to Long.serializer(),
        "block_enc_hash" to Long.serializer(),
        "block_enc_sig" to Long.serializer(),
    )

    override fun supports(candidate: ZkSystemSpec): Boolean {
        return zkSystemSpecs.any { supportedSpec ->
            supportedSpec.system == candidate.system
                    && candidate.params["circuit_hash"] != null
                    && candidate.params["circuit_hash"] == supportedSpec.params["circuit_hash"]
        }
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
            .filter { this.supports(it) }
            .map { it.toMultipazZkSystemSpec() }
        val matchingSupportedSpec = backend.getMatchingSystemSpec(multipazZkSystemSpecs, multipazRequestedClaims)

        return matchingSupportedSpec?.let { spec ->
            val id = multipazZkSystemSpecs.first { it.params["circuit_hash"] == spec.params["circuit_hash"] }.id
            spec.copyWithParameters(id = id)
        }

    }

    override suspend fun generate(
        request: PresentationRequestParameters,
        credential: StoreEntry.Iso,
        requestedClaims: Collection<NormalizedJsonPath>,
        zkSystemSpecs: List<ZkSystemSpec>,
        keyMaterial: KeyMaterial
    ): KmmResult<IsoMdocZkProof> = catching {
        val sessionTranscript = requireNotNull(request.calcIsoSessionTranscript()) {
            "calcIsoSessionTranscript not implemented"
        }
        val multipazSessionTranscript = sessionTranscript.toMultipazSessionTranscript()
        val selectedMultipazZkSystemSpec = requireNotNull(chooseZkSystemSpec(credential, requestedClaims, zkSystemSpecs)) {
            IllegalStateException("No matching ZK system spec found")
        }
        val selectedZkSystemSpec = selectedMultipazZkSystemSpec.toZkSystemSpec()
        val signDeviceAuthDetached = SignCoseDetached<ByteArray>(
            keyMaterial = keyMaterial,
            protectedHeaderModifier = CoseHeaderNone(),
            unprotectedHeaderModifier = CoseHeaderNone()
        )
        val plainMultipazDocument = credential
            .discloseRequestedClaims(requestedClaims, sessionTranscript, signDeviceAuthDetached)
            .toMultipazDocument()
        val timestamp = Clock.System.now().truncateToSeconds()

        val multipazZkDocument = backend.generateProof(
            zkSystemSpec = selectedMultipazZkSystemSpec,
            document = plainMultipazDocument,
            sessionTranscript = multipazSessionTranscript,
            timestamp = timestamp,
        )

        val zkDocument = multipazZkDocument.toZkDocument()
        IsoMdocZkProof(
            zkDocument = zkDocument,
            verifyFn = createVerifyFn(selectedZkSystemSpec, sessionTranscript),
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
            verifyFn = createVerifyFn(zkSystemSpec, sessionTranscript),
        )
    }

    private fun createVerifyFn(
        zkSystemSpec: ZkSystemSpec,
        sessionTranscript: SessionTranscript,
    ): suspend (ZkDocument) -> KmmResult<Unit> = {
        catching {
            backend.verifyProof(
                zkDocument = it.toMultipazZkDocument(),
                zkSystemSpec = zkSystemSpec.toMultipazZkSystemSpec(),
                sessionTranscript = sessionTranscript.toMultipazSessionTranscript(),
            )
        }
    }


    override suspend fun initialize(): KmmResult<Unit> = catching {
        backend = LongfellowZkSystem().also { it.addDefaultCircuits() }
    }
}