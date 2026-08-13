package at.asitplus.wallet.lib.cbor

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
import at.asitplus.wallet.lib.zk.iso.IsoMdocZkBackend
import at.asitplus.wallet.lib.zk.iso.IsoMdocZkProof
import io.github.aakira.napier.Napier
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.Uint
import org.multipaz.cbor.buildCborMap
import org.multipaz.mdoc.response.MdocDocument
import org.multipaz.mdoc.zkp.ZkSystemParamValue
import org.multipaz.mdoc.zkp.ZkSystemSpec as MultipazZkSystemSpec
import org.multipaz.mdoc.zkp.longfellow.LongfellowZkSystem
import kotlin.ByteArray
import kotlin.time.Clock

class LongfellowZkBackend : IsoMdocZkBackend {
    private lateinit var backend: LongfellowZkSystem
    private var zkSystemsMap: Map<MultipazZkSystemSpec, ZkSystemSpec> = emptyMap()

    override val system: String get() = backend.name

    override val zkSystemSpecs:  List<ZkSystemSpec> get() = zkSystemsMap.values.toList()
    override val paramSerializers: Map<String, KSerializer<*>> = mapOf(
        "version" to Int.serializer(),
        "circuit_hash" to String.serializer(),
        "num_attributes" to Int.serializer(),
        "block_enc_hash" to Int.serializer(),
        "block_enc_sig" to Int.serializer(),
    )


    override suspend fun generate(
        request: PresentationRequestParameters,
        credential: StoreEntry.Iso,
        requestedClaims: Collection<NormalizedJsonPath>,
        zkSystemSpecs: List<ZkSystemSpec>,
        keyMaterial: KeyMaterial
    ): KmmResult<IsoMdocZkProof> = catching {
        // 1. Generate the document directly using Verifiable Presentation Factory
        // 2. Convert the document into one for multipaz (serialize and then deserialize again)
        // 3. Convert the resulting multipaz Zk Document back into a ZkDocument for VCK (serialize and deserialize again)
        // 4. Read out the document id and match it with the ZkSystems in the list
        // 5. Use the matched zksystem and the zkdocument to form an IsoMdocZkLon.gfellowProof

        // OK new Branch. we fix the naming for ZKSystemSpec. we do use an open ZkSystem class and DCQL verison is just in implementaiton of that. get rid of the unnecessary data class here.
        // new branch also gets the supported ones and the key material

        // In general systems could assemble the proof themselves using the credential + the key material/signer, right? in that case we should expose those values here:
        // So I think it is better to instead expose the whole request, credential, key material + optional signer with default value and
        // then do it on our own. it is better than a callback or a VerifiablePresentationFactory because it is compatible with potential future schemes!

        // Convert zkSystemSpec and choose a fitting one:
        val sessionTranscript = request.calcIsoSessionTranscript()?.toMultipazSessionTranscript()
            ?: throw IllegalStateException("No Session Transcript Callback provided")

        val multipazZkSystemSpecs = zkSystemSpecs
            .filter { it.params["version"] == 7L && it.params["num_attributes"] == 1L }
            .map { it.toMultipazZkSystemSpec() }

        // TODO: replace using backend.getMatchingSystemSpec eventually
        val selectedMultipazZkSystemSpec = multipazZkSystemSpecs.single()
        val selectedZkSystemSpec = selectedMultipazZkSystemSpec.toAsitplusZkSystemSpec()

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
                ?: credential.issuerSigned.issuerAuth.protectedHeader.certificateChain
                    ?.takeIf { it.isNotEmpty() }
                ?: credential.issuerSigned.issuerAuth.unprotectedHeader?.certificateChain
                    ?.takeIf { it.isNotEmpty() }
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
        zkSystemsMap = backend.systemSpecs.associateWith {
            ZkSystemSpec(
                id = it.id,
                system = it.system,
                params = it.params.entries.associate { (key, value) ->
                    key to when (value) {
                        is ZkSystemParamValue.BooleanValue -> value.value
                        is ZkSystemParamValue.StringValue -> value.value
                        is ZkSystemParamValue.DoubleValue -> value.value
                        is ZkSystemParamValue.LongValue -> value.value
                    }
                }
            )
        }
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
    signDeviceAuthDetached: SignCoseDetachedFun<ByteArray>
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
        calculateDeviceSignature(input, sessionTranscript, signDeviceAuthDetached)
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
    signDeviceAuthDetached: SignCoseDetachedFun<ByteArray>
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

suspend fun ZkDocument.toMultipazZkDocument(): MdocDocument {
    val serializedZkDocument = coseCompliantSerializer.encodeToByteArray(this)
    return MdocDocument.fromDataItem(Cbor.decode(serializedZkDocument))
}

fun ZkSystemSpec.toMultipazZkSystemSpec(): MultipazZkSystemSpec {
    val spec = MultipazZkSystemSpec(id, system)
    params.forEach {
        when (it.value) {
            is String -> spec.addParam(it.key, it.value as String)
            is Int -> spec.addParam(it.key, (it.value as Int).toLong())
            is Long -> spec.addParam(it.key, it.value as Long)
            is Boolean -> spec.addParam(it.key, it.value as Boolean)
            else -> throw IllegalArgumentException("Unsupported parameter value type: ${it.value} for  ${it.key}")
        }
    }
    return spec
}

suspend fun Document.toMultipazDocument(): MdocDocument {
    val serializedZkDocument = coseCompliantSerializer.encodeToByteArray(this)
    return MdocDocument.fromDataItem(Cbor.decode(serializedZkDocument))
}

fun SessionTranscript.toMultipazSessionTranscript(): DataItem {
    val serialized = coseCompliantSerializer.encodeToByteArray(this)
    return Cbor.decode(serialized)
}

fun org.multipaz.mdoc.zkp.ZkDocument.toZkDocument(): ZkDocument {
    // Multipaz follows COSE_X509 and encodes a one-certificate chain as a single
    // byte string, while VCK models the same field as List<ByteArray> and its
    // serializer expects an array. Normalize only that singleton representation
    // at the interop boundary; multi-certificate chains are already arrays.
    val multipazDocument = this.toDataItem()
    val docDataKey = multipazDocument.asMap.keys.find { (it is Tstr && it.asTstr == "documentData") }
        ?: multipazDocument.asMap.keys.first()
    val documentData = multipazDocument[docDataKey] as Tagged
    val documentDataItem = Cbor.decode(documentData.asTagged.asBstr)

    val certKey = documentDataItem.asMap.keys.find {
        (it is Tstr && it.asTstr == "msoX5chain") || it == Uint(33uL)
    }
    val certificateChain = certKey?.let { documentDataItem.asMap[it] }

    val normalizedEncoded = if (certificateChain is Bstr) {
        val normalizedDocumentData = buildCborMap {
            documentDataItem.asMap.forEach { (key, value) ->
                if (key == certKey) {
                    put(key, CborArray(mutableListOf(value)))
                } else {
                    put(key, value)
                }
            }
        }
        buildCborMap {
            multipazDocument.asMap.forEach { (key, value) ->
                if (key == docDataKey) {
                    put(key, Tagged(Tagged.ENCODED_CBOR, Bstr(Cbor.encode(normalizedDocumentData))))
                } else {
                    put(key, value)
                }
            }
        }
    } else {
        multipazDocument
    }
    val encoded = Cbor.encode(item = normalizedEncoded)
    val zkDocument = coseCompliantSerializer.decodeFromByteArray<ZkDocument>(encoded)
    return zkDocument
}

fun MultipazZkSystemSpec.toAsitplusZkSystemSpec(): ZkSystemSpec {
    val outParams = mutableMapOf<String, Any>()
    params.forEach { (key, paramValue) ->
        when (paramValue) {
            is ZkSystemParamValue.StringValue -> outParams[key] = paramValue.value
            is ZkSystemParamValue.LongValue -> outParams[key] = paramValue.value
            is ZkSystemParamValue.DoubleValue -> outParams[key] = paramValue.value
            is ZkSystemParamValue.BooleanValue -> outParams[key] = paramValue.value
        }
    }
    val spec = ZkSystemSpec(id, system, outParams)

    return spec
}
