package at.asitplus.iso.zk.longfellowZk

import at.asitplus.KmmResult
import at.asitplus.iso.DeviceAuth
import at.asitplus.iso.DeviceNameSpaces
import at.asitplus.iso.DeviceResponse
import at.asitplus.iso.DeviceSigned
import at.asitplus.iso.DeviceSignedItemList
import at.asitplus.iso.Document
import at.asitplus.iso.IssuerSigned
import at.asitplus.iso.IssuerSignedItem
import at.asitplus.iso.IssuerSignedList
import at.asitplus.iso.SessionTranscript
import at.asitplus.iso.ValidityInfo
import at.asitplus.iso.ZkDocument
import at.asitplus.iso.ZkSignedItem
import at.asitplus.iso.ZkSignedList
import at.asitplus.iso.ZkSystem
import at.asitplus.wallet.lib.iso.zk.IsoMdocZkProof
import at.asitplus.wallet.lib.iso.zk.IsoMdocZkProofFactory
import at.asitplus.iso.zk.longfellowZk.backend.LongfellowZkBackend
import at.asitplus.iso.zk.longfellowZk.backend.NativeLongfellowZkBackend
import at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider.BasicHandleAndCircuitProvider
import at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider.FileBasedPersistentHandleAndCircuitProvider
import at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider.HandleAndCircuitProvider
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.jsonpath.core.NormalizedJsonPathSegment
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.cosef.io.ByteStringWrapper
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.agent.IsoDeviceSignatureInput
import at.asitplus.wallet.lib.agent.PresentationException
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.agent.SubjectCredentialStore.StoreEntry
import com.ionspin.kotlin.bignum.modular.ModularBigInteger
import io.github.aakira.napier.Napier
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import kotlinx.io.files.Path
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToByteArray
import kotlin.time.Clock
import kotlin.time.Instant

class IsoMdocLongfellowZKProof private constructor(
    override val zkSystem: ZkSystem,
    override val timestamp: LocalDate,
    override val issuerZkSignedNamespaces: Map<String, ZkSignedList>,
    override val deviceZkSignedNamespaces: Map<String, ZkSignedList>,
    override val rawProof: ByteArray,
    override val docType: String,
    override val msoX5Chain: List<ByteArray>?,
    private val sessionTranscript: SessionTranscript,
    private val factory: Factory,
) : IsoMdocZkProof() {

    private val issuerKey: CryptoPublicKey.EC = extractIssuerKey(msoX5Chain)
    private val zkParams: ZkParams = buildParams(
        zkSystem = zkSystem,
        provider = factory.handleAndCircuitProvider
    )

    override fun verify(): Boolean {
        val requestedItems = issuerZkSignedNamespaces.toRequestedItems()
        if (!requestedItems.all { it.isValid() }) return false
        val encodedTimestamp = timestamp
            .atTime(LocalTime(0, 0, 0))
            .toInstant(TimeZone.UTC)
            .toString()
        val (issuerKeyX, issuerKeyY) = issuerKey.toPrefixedHexString()
        val sessionTranscriptBytes = coseCompliantSerializer.encodeToByteArray(sessionTranscript)

        return factory.backend.verifyProof(
            circuit = zkParams.circuit,
            publicKeyX = issuerKeyX,
            publicKeyY = issuerKeyY,
            transcript = sessionTranscriptBytes,
            requestedItems = requestedItems,
            timestamp = encodedTimestamp,
            proof = rawProof,
            docType = docType,
            zkSpec = zkParams.handle
        ).getOrThrow()
    }

    class Factory(
        internal val backend: LongfellowZkBackend,
        handleAndCircuitProviderFn: (LongfellowZkBackend) -> HandleAndCircuitProvider
    ) : IsoMdocZkProofFactory {
        internal val handleAndCircuitProvider = handleAndCircuitProviderFn.invoke(backend)

        override val systemName = SYSTEM_NAME
        override val paramSerializers = longfellowParamSerializers

        override fun supports(zkSystem: ZkSystem): Boolean {
            // TODO: Consider more validation eg. with
            //  attribute count and circuit validation with
            //  val attributeCount = namespaces.values.sumOf { it.entries.size }
            return zkSystem.system == SYSTEM_NAME &&
                    zkSystem.params.containsKey(CIRCUIT_HASH_IDENTIFIER)
        }

        override fun initialize(): KmmResult<Unit> = backend.initialize()

        override suspend fun generate(
            request: PresentationRequestParameters,
            credential: StoreEntry.Iso,
            requestedClaims: Collection<NormalizedJsonPath>,
            zkSystems: List<ZkSystem>
        ): IsoMdocZkProof {
            require(zkSystems.all{ supports(it) }) { " Incompatible ZkSystem! " }

            val sessionTranscript = requireNotNull(request.sessionTranscript) { "Session transcript required" }
            val document = credential.discloseRequestedClaims(requestedClaims, request)

            require(document.issuerSigned.namespaces?.size == 1) { "Longfellow only support credentials with one namespace!" }
            val countDisclosedAttributes = document.issuerSigned.namespaces?.values?.sumOf { it.entries.size } ?: 0

            val zkSystem = zkSystems.firstOrNull { it.params.containsKey(NUM_ATTRIBUTES_IDENTIFIER) && it.params[NUM_ATTRIBUTES_IDENTIFIER] == countDisclosedAttributes }
                ?: throw PresentationException("No ZkSystem found that supports the number of disclosed attributes!")

            // TODO: remove this check after migrating to new upstream codebase
            require(isIso8601Compliant(document.issuerSigned.issuerAuth.payload?.validityInfo)) {
                "Timestamps do not follow ISO-8601 (precision to seconds)"
            }

            val msoX5Chain = document.issuerSigned.issuerAuth.unprotectedHeader?.certificateChain
            val issuerKey = extractIssuerKey(msoX5Chain)
            val zkParams = buildParams(
                zkSystem = zkSystem,
                provider = handleAndCircuitProvider
            )

            val issuerZkSignedItems = document.issuerSigned.namespaces.toNamespacedIssuerZkSignedList()
            val requestedItems = issuerZkSignedItems.toRequestedItems()
            require(requestedItems.all { it.isValid() }) {
                "Prover can't compute with requested item!"
            }

            val docType = document.docType
            val deviceZkSignedNamespaces = document.deviceSigned.namespaces.value.entries.toNamespacedDeviceZkSignedList()

            val deviceResponse = DeviceResponse(
                version = "1.0",
                documents = arrayOf(document),
                status = 0U,
            )
            val deviceResponseBytes = coseCompliantSerializer.encodeToByteArray(deviceResponse)
            val transcriptBytes = coseCompliantSerializer.encodeToByteArray(sessionTranscript)
            val now = Clock.System.todayIn(TimeZone.UTC)

            val encodedNow = now.atTime(LocalTime(0, 0, 0))
                .toInstant(TimeZone.UTC)
                .toString()

            val (issuerPublicKeyX, issuerPublicKeyY) = issuerKey.toPrefixedHexString()

            val rawProof = backend.generateProof(
                zkParams.circuit, deviceResponseBytes,
                issuerPublicKeyX, issuerPublicKeyY,
                transcriptBytes, encodedNow,
                requestedItems,
                zkParams.handle
            ).getOrThrow()

            return IsoMdocLongfellowZKProof(
                zkSystem = zkSystem,
                timestamp = now,
                issuerZkSignedNamespaces = issuerZkSignedItems,
                deviceZkSignedNamespaces = deviceZkSignedNamespaces,
                rawProof = rawProof,
                docType = docType,
                msoX5Chain = msoX5Chain,
                sessionTranscript = sessionTranscript,
                factory = this,
            )
        }

        override fun load(
            zkDocument: ZkDocument,
            sessionTranscript: SessionTranscript,
            zkSystem: ZkSystem
        ): IsoMdocZkProof {
            require( supports(zkSystem)) { " Incompatible ZkSystem: $zkSystem! " }

            val timestamp = zkDocument.zkDocumentDataBytes.value.timestamp
//            require(timestamp.nanosecondsOfSecond == 0) { "Timestamp does not conform to ISO 8601" }

            val msoX5Chain = zkDocument.zkDocumentDataBytes.value.certificateChain
            val docType = zkDocument.zkDocumentDataBytes.value.docType
            val rawProof = zkDocument.proof

            // TODO: consider requiring that deviceSigned is null, because Longfellow doesn't support them yet
            val deviceZkSignedNamespaces = zkDocument.zkDocumentDataBytes.value.deviceSigned ?: emptyMap()
            val issuerZkSignedNamespaces = zkDocument.zkDocumentDataBytes.value.issuerSigned ?: emptyMap()

            return IsoMdocLongfellowZKProof(
                zkSystem = zkSystem,
                timestamp = timestamp,
                issuerZkSignedNamespaces = issuerZkSignedNamespaces,
                deviceZkSignedNamespaces = deviceZkSignedNamespaces,
                rawProof = rawProof,
                docType = docType,
                msoX5Chain = msoX5Chain,
                sessionTranscript = sessionTranscript,
                factory = this,
            )
        }



    }

    companion object {
        private const val CIRCUIT_HASH_IDENTIFIER = "circuit_hash"
        private const val NUM_ATTRIBUTES_IDENTIFIER = "num_attributes"
        private const val VERSION_IDENTIFIER = "version"
        private const val BLOCK_ENC_HASH_IDENTIFIER = "block_enc_hash"
        private const val BLOCK_ENC_SIG_IDENTIFIER = "block_enc_sig"
        private const val SYSTEM_NAME = "longfellow-libzk-v1"


        private val longfellowParamSerializers = mapOf(
            CIRCUIT_HASH_IDENTIFIER to String.serializer(),
            NUM_ATTRIBUTES_IDENTIFIER to Int.serializer(),
            VERSION_IDENTIFIER to Int.serializer(),
            BLOCK_ENC_HASH_IDENTIFIER to Int.serializer(),
            BLOCK_ENC_SIG_IDENTIFIER to Int.serializer(),
        )

        val Default: IsoMdocZkProofFactory by lazy {
            Factory(NativeLongfellowZkBackend) { backend ->
                FileBasedPersistentHandleAndCircuitProvider(
                    BasicHandleAndCircuitProvider(backend), Path(SYSTEM_NAME)
                )
            }
        }

        // TODO: consider checking the whole certificate chain
        private fun extractIssuerKey(msoX5Chain: List<ByteArray>?): CryptoPublicKey.EC {
            val certificateHead = requireNotNull(msoX5Chain?.firstOrNull()) {
                "No issuer certificate in header"
            }
            val x509Certificate = X509Certificate.decodeFromDerSafe(certificateHead).getOrElse {
                error("Could not parse issuer certificate from header: ${it.message}")
            }
            return x509Certificate.decodedPublicKey.getOrNull() as? CryptoPublicKey.EC
                ?: error("Could not parse EC key from certificate")
        }

        private fun buildParams(
            zkSystem: ZkSystem,
            provider: HandleAndCircuitProvider
        ) = ZkParams(
            systemName = SYSTEM_NAME,
            circuitId = requireNotNull(zkSystem.params[CIRCUIT_HASH_IDENTIFIER] as? String) {
                "No circuit hash provided"
            },
            provider = provider
        )
    }
}

// TODO: delete this. It is just a sanity check, because during development, we didn't properly follow the ISO spec
private fun isIso8601Compliant(validityInfo: ValidityInfo?): Boolean {
    return validityInfo?.let {
        it.validFrom.nanosecondsOfSecond == 0 &&
                it.validUntil.nanosecondsOfSecond == 0 &&
                it.signed.nanosecondsOfSecond == 0
    } ?: false
}

private fun Map<String, IssuerSignedList>?.toNamespacedIssuerZkSignedList(): Map<String, ZkSignedList> =
    this?.mapValues { (_, issuerList) ->
        ZkSignedList(
            entries = issuerList.entries.map { entry ->
                ZkSignedItem(
                    elementIdentifier = entry.value.elementIdentifier,
                    elementValue = entry.value.elementValue
                )
            }
        )
    } ?: emptyMap()

private fun Map<String, DeviceSignedItemList>?.toNamespacedDeviceZkSignedList(): Map<String, ZkSignedList> =
    this?.mapValues { (_, deviceSignedList) ->
        ZkSignedList(
            entries = deviceSignedList.entries.map { entry ->
                ZkSignedItem(
                    elementIdentifier = entry.key,
                    elementValue = entry.value
                )
            }
        )
    } ?: emptyMap()

private fun Map<String, ZkSignedList>.toRequestedItems() = flatMap { (namespace, itemList) ->
    itemList.entries.map { item ->
        RequestedItem(namespace, item.elementIdentifier, item.elementValue)
    }
}

private fun Instant.toIso8601(): String {
    require(this.nanosecondsOfSecond == 0) { "Instance of 'Instant' is not ISO 8601 compatible" }
    return this.toString()
}

private fun ModularBigInteger.toPrefixedHexString() = "0x${this.toString(16)}"
private fun CryptoPublicKey.EC.toPrefixedHexString() = this.x.toPrefixedHexString() to this.y.toPrefixedHexString()

private suspend fun StoreEntry.Iso.discloseRequestedClaims(
    requestedClaims: Collection<NormalizedJsonPath>,
    request: PresentationRequestParameters,
): Document {
    // grouping by namespace and all requested claims for that namespace
    val namespaceToAttributesMap: Map<String, List<String>> = requestedClaims
        .mapNotNull { it.toIsoNamespaceAttribute() }
        .groupBy { it.first }
        .mapValues { it.value.map { it.second } }
    val disclosedItems = namespaceToAttributesMap.mapValues { entry ->
        entry.value.map {
            discloseItem(entry.key, it)
        }
    }

    val docType = schemeIdentifier
        ?: issuerSigned.issuerAuth.payload?.docType
        ?: resolveScheme().isoDocType
        ?: throw PresentationException("Scheme not known or not registered")
    val deviceNameSpaceBytes = ByteStringWrapper(DeviceNameSpaces(mapOf()))
    val input = IsoDeviceSignatureInput(docType, deviceNameSpaceBytes)
    val deviceSignature = request.calcIsoDeviceSignaturePlain(input)
        ?: throw PresentationException("calcIsoDeviceSignature not implemented")

    return Document(
        docType = docType,
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

private fun StoreEntry.Iso.discloseItem(
    namespace: String,
    attributeName: String
): IssuerSignedItem = issuerSigned.namespaces?.get(namespace)
    ?.entries?.find { it.value.elementIdentifier == attributeName }
    ?.value
    ?: throw PresentationException("Attribute not available in credential: $['$namespace']['$attributeName']")