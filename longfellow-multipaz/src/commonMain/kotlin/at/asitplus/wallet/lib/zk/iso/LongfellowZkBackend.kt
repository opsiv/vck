package at.asitplus.wallet.lib.zk.iso

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.iso.DeviceAuth
import at.asitplus.iso.DeviceAuthentication
import at.asitplus.iso.DeviceNameSpaces
import at.asitplus.iso.DeviceSigned
import at.asitplus.iso.Document
import at.asitplus.iso.IssuerSigned
import at.asitplus.iso.IssuerSignedItem
import at.asitplus.iso.SessionTranscript
import at.asitplus.iso.ZkDocument
import at.asitplus.iso.ZkSystemSpec
import at.asitplus.iso.wrapInCborTag
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.jsonpath.core.NormalizedJsonPathSegment
import at.asitplus.openid.truncateToSeconds
import at.asitplus.signum.indispensable.cosef.CoseSigned
import at.asitplus.signum.indispensable.cosef.io.ByteStringWrapper
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.wallet.lib.agent.IsoDeviceSignatureInput
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.agent.PresentationException
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.SubjectCredentialStore.StoreEntry
import at.asitplus.wallet.lib.cbor.CoseHeaderNone
import at.asitplus.wallet.lib.cbor.SignCoseDetached
import io.github.aakira.napier.Napier
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToByteArray
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.mdoc.response.MdocDocument
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
        val multipazZkSystemSpecs = zkSystemSpecs
            .filter { this.supports(it) }
            .map { it.toMultipazZkSystemSpec() }

        val multipazRequestedClaims = requestedClaims.map {
            it.toMdocRequestedClaim(credential.schemeIdentifier)
        }
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
        val sessionTranscript = request.calcIsoSessionTranscript()?.toMultipazSessionTranscript()
            ?: throw IllegalStateException("No Session Transcript Callback provided")

        val selectedMultipazZkSystemSpec = chooseZkSystemSpec(credential, requestedClaims, zkSystemSpecs)
            ?: throw IllegalStateException("No matching ZK system spec found")

        val selectedZkSystemSpec = selectedMultipazZkSystemSpec.toZkSystemSpec()

        val signDeviceAuthDetached = SignCoseDetached<ByteArray>(
            keyMaterial = keyMaterial,
            protectedHeaderModifier = CoseHeaderNone(),
            unprotectedHeaderModifier = CoseHeaderNone()
        )

        val plainDocument = credential
            .discloseRequestedClaims(requestedClaims, request, signDeviceAuthDetached)
            .getOrThrow()
            .toMultipazDocument()

        val timestamp = Clock.System.now().truncateToSeconds()

        val zkDocument = backend.generateProof(
            zkSystemSpec = selectedMultipazZkSystemSpec,
            document = plainDocument,
            sessionTranscript = sessionTranscript,
            timestamp = timestamp
        ).toZkDocument()

        MultipazLongfellowIsoMdocZkProof(
            zkSystemSpec = selectedZkSystemSpec,
            rawProof = zkDocument.proof,
            docType = zkDocument.zkDocumentDataBytes.value.docType,
            timestamp = zkDocument.zkDocumentDataBytes.value.timestamp,
            issuerZkSignedNamespaces = zkDocument.zkDocumentDataBytes.value.issuerSigned ?: emptyMap(),
            deviceZkSignedNamespaces = zkDocument.zkDocumentDataBytes.value.deviceSigned ?: emptyMap(),
            msoX5Chain = zkDocument.zkDocumentDataBytes.value.certificateChain.takeIf { !it.isNullOrEmpty() }
        )
    }

    override fun load(
        zkDocument: ZkDocument,
        sessionTranscript: SessionTranscript,
        zkSystemSpec: ZkSystemSpec
    ): IsoMdocZkProof {
        TODO("Not yet implemented")
    }

    override suspend fun initialize(): KmmResult<Unit> = catching {
        backend = LongfellowZkSystem().also { it.addDefaultCircuits() }
    }
}

private fun ZkSystemParamValue.matches(value: Any?): Boolean = when (this) {
    is ZkSystemParamValue.StringValue -> value == this.value
    is ZkSystemParamValue.BooleanValue -> value == this.value
    is ZkSystemParamValue.DoubleValue -> value == this.value
    is ZkSystemParamValue.LongValue -> when (value) {
        is Long -> value == this.value
        is Int -> value.toLong() == this.value
        else -> false
    }
}

private suspend fun StoreEntry.Iso.discloseRequestedClaims(
    requestedClaims: Collection<NormalizedJsonPath>,
    request: PresentationRequestParameters,
    signDeviceAuthDetached: at.asitplus.wallet.lib.cbor.SignCoseDetachedFun<ByteArray>
): KmmResult<Document> = catching {
    // grouping by namespace and all requested claims for that namespace
    // TODO: Consider a check that we only have one namespace here because Longfellow-ZK currently only supports one Namespace (require or sth)

    val namespaceToAttributesMap: Map<String, List<String>> = requestedClaims
        .mapNotNull { it.toIsoNamespaceAttribute() }
        .groupBy { it.first }
        .mapValues { it.value.map { it.second } }
    val disclosedItems = namespaceToAttributesMap.mapValues { entry ->
        entry.value.map {
            discloseItem(entry.key, it)
        }
    }

    val deviceNameSpaceBytes = ByteStringWrapper(DeviceNameSpaces(mapOf()))
    val input = IsoDeviceSignatureInput(schemeIdentifier, deviceNameSpaceBytes)

    @Suppress("DEPRECATION")
    val deviceSignature = request.calcIsoDeviceSignaturePlain(input) ?: run {
        val sessionTranscript = request.calcIsoSessionTranscript()
            ?: throw PresentationException("calcIsoSessionTranscript not implemented")
        calculateDeviceSignature(
            input,
            sessionTranscript,
            signDeviceAuthDetached
        )
    }

    Document(
        docType = schemeIdentifier,
        issuerSigned = IssuerSigned.fromIssuerSignedItems(
            namespacedItems = disclosedItems,
            issuerAuth = issuerSigned.issuerAuth
        ),
        deviceSigned = DeviceSigned(
            namespaces = deviceNameSpaceBytes,
            deviceAuth = DeviceAuth(
                deviceSignature = deviceSignature
            )
        )
    )
}

private fun StoreEntry.Iso.discloseItem(
    namespace: String,
    attributeName: String
): IssuerSignedItem = issuerSigned.namespaces?.get(namespace)
    ?.entries?.find { it.value.elementIdentifier == attributeName }
    ?.value
    ?: throw PresentationException("Attribute not available in credential: $['$namespace']['$attributeName']")

/** Returns map of first element (namespace) to second element (attribute name) */
private fun NormalizedJsonPath.toIsoNamespaceAttribute() = with(firstTwoSegments()) {
    if (size == 2) {
        first().memberName to last().memberName
    } else {
        // Treating non-namespaced attributes as fields that are inherent to the credential for now
        //  -> no need for selective disclosure
        Napier.w("Not a namespaced attribute, ignoring: $this. This may be a bug.")
        null
    }
}

private fun NormalizedJsonPath.firstTwoSegments() = segments.take(2)
    .filterIsInstance<NormalizedJsonPathSegment.NameSegment>()


private suspend fun calculateDeviceSignature(
    input: IsoDeviceSignatureInput,
    sessionTranscript: SessionTranscript,
    signDeviceAuthDetached: at.asitplus.wallet.lib.cbor.SignCoseDetachedFun<ByteArray>
):  CoseSigned<ByteArray> {
    val deviceAuthentication = DeviceAuthentication(
        type = DeviceAuthentication.TYPE,
        sessionTranscript = sessionTranscript,
        docType = input.docType,
        namespaces = input.deviceNameSpaceBytes
    )
    val deviceAuthenticationBytes = coseCompliantSerializer
        .encodeToByteArray(ByteStringWrapper(deviceAuthentication))
        .wrapInCborTag(24)
    Napier.d("Device authentication signature input is ${deviceAuthenticationBytes.toHexString()}")
    return signDeviceAuthDetached(
        protectedHeader = null,
        unprotectedHeader = null,
        payload = deviceAuthenticationBytes,
        serializer = ByteArraySerializer()
    ).getOrElse { e ->
        Napier.w("Could not create DeviceAuth for presentation", e)
        throw PresentationException(e)
    }
}





suspend fun Document.toMultipazDocument(): MdocDocument {
    val serializedZkDocument = coseCompliantSerializer.encodeToByteArray(this)
    return MdocDocument.fromDataItem(Cbor.decode(serializedZkDocument))
}

fun SessionTranscript.toMultipazSessionTranscript(): DataItem {
    val serialized = coseCompliantSerializer.encodeToByteArray(this)
    return Cbor.decode(serialized)
}

