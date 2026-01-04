package at.asitplus.iso.zk.longfellowZk

import at.asitplus.KmmResult
import at.asitplus.iso.DeviceResponse
import at.asitplus.iso.DeviceSignedItemList
import at.asitplus.iso.Document
import at.asitplus.iso.IssuerSignedList
import at.asitplus.iso.SessionTranscript
import at.asitplus.iso.ValidityInfo
import at.asitplus.iso.ZkDocument
import at.asitplus.iso.ZkSignedItem
import at.asitplus.iso.ZkSignedList
import at.asitplus.iso.ZkSystemSpec
import at.asitplus.iso.zk.IsoMdocZkProof
import at.asitplus.iso.zk.IsoMdocZkProofFactory
import at.asitplus.iso.zk.longfellowZk.backend.LongfellowZkBackend
import at.asitplus.iso.zk.longfellowZk.backend.NativeLongfellowZkBackend
import at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider.BasicHandleAndCircuitProvider
import at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider.FileBasedPersistentHandleAndCircuitProvider
import at.asitplus.iso.zk.longfellowZk.handleAndCircuitProvider.HandleAndCircuitProvider
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.openid.truncateToSeconds
import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.agent.build
import com.ionspin.kotlin.bignum.modular.ModularBigInteger
import kotlinx.io.files.Path
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToByteArray
import kotlin.time.Clock
import kotlin.time.Instant

class IsoMdocLongfellowZKProof private constructor(
    override val zkSystemSpec: ZkSystemSpec,
    override val timestamp: Instant,
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
        zkSystemSpec = zkSystemSpec,
        provider = factory.handleAndCircuitProvider
    )

    override fun verify(): Boolean {
        val requestedItems = issuerZkSignedNamespaces.toRequestedItems()
        if (!requestedItems.all { it.isValid() }) return false

        val (issuerKeyX, issuerKeyY) = issuerKey.toPrefixedHexString()
        val encodedTimestamp = timestamp.toIso8601()
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

        override fun supports(zkSystemSpec: ZkSystemSpec): Boolean {
            // TODO: Consider more validation eg. with
            //  attribute count and circuit validation with
            //  val attributeCount = namespaces.values.sumOf { it.entries.size }
            return zkSystemSpec.system == SYSTEM_NAME &&
                    zkSystemSpec.params.containsKey(CIRCUIT_HASH_IDENTIFIER)
        }

        override fun initialize(): KmmResult<Unit> = backend.initialize()

        override suspend fun generate(
            request: PresentationRequestParameters,
            credential: SubjectCredentialStore.StoreEntry.Iso,
            requestedClaims: Collection<NormalizedJsonPath>,
            zkSystemSpec: ZkSystemSpec
        ): IsoMdocZkProof {
            requireSupported(zkSystemSpec)

            val sessionTranscript = requireNotNull(request.sessionTranscript) { "Session transcript required" }
            val document = Document.build(
                request = request,
                credential = credential,
                requestedClaims = requestedClaims,
            )

            // TODO: remove this check after migrating to new upstream codebase
            require(isIso8601Compliant(document.issuerSigned.issuerAuth.payload?.validityInfo)) {
                "Timestamps do not follow ISO-8601 (precision to seconds)"
            }

            val msoX5Chain = document.issuerSigned.issuerAuth.unprotectedHeader?.certificateChain
            val issuerKey = extractIssuerKey(msoX5Chain)
            val zkParams = buildParams(
                zkSystemSpec = zkSystemSpec,
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
            val now = Clock.System.now().truncateToSeconds()
            val encodedNow = now.toIso8601()

            val (issuerPublicKeyX, issuerPublicKeyY) = issuerKey.toPrefixedHexString()

            val rawProof = backend.generateProof(
                zkParams.circuit, deviceResponseBytes,
                issuerPublicKeyX, issuerPublicKeyY,
                transcriptBytes, encodedNow,
                requestedItems,
                zkParams.handle
            ).getOrThrow()

            return IsoMdocLongfellowZKProof(
                zkSystemSpec = zkSystemSpec,
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
            zkSystemSpec: ZkSystemSpec
        ): IsoMdocZkProof {
            requireSupported(zkSystemSpec)

            val timestamp = zkDocument.zkDocumentDataBytes.value.timestamp
            require(timestamp.nanosecondsOfSecond == 0) { "Timestamp does not conform to ISO 8601" }

            val msoX5Chain = zkDocument.zkDocumentDataBytes.value.certificateChain
            val docType = zkDocument.zkDocumentDataBytes.value.docType
            val rawProof = zkDocument.proof

            // TODO: consider requiring that deviceSigned is null, because Longfellow doesn't support them yet
            val deviceZkSignedNamespaces = zkDocument.zkDocumentDataBytes.value.deviceSigned ?: emptyMap()
            val issuerZkSignedNamespaces = zkDocument.zkDocumentDataBytes.value.issuerSigned ?: emptyMap()

            return IsoMdocLongfellowZKProof(
                zkSystemSpec = zkSystemSpec,
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

        private fun requireSupported(zkSystemSpec: ZkSystemSpec) {
            require(supports(zkSystemSpec)) { "Incompatible ZkSystem: ${zkSystemSpec.system}" }
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
            zkSystemSpec: ZkSystemSpec,
            provider: HandleAndCircuitProvider
        ) = ZkParams(
            systemName = SYSTEM_NAME,
            circuitId = requireNotNull(zkSystemSpec.params[CIRCUIT_HASH_IDENTIFIER] as? String) {
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
