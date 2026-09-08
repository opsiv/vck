package at.asitplus.wallet.lib.zk.iso

import at.asitplus.data.NonEmptyList.Companion.nonEmptyListOf
import at.asitplus.iso.DeviceRequest
import at.asitplus.iso.DocRequest
import at.asitplus.iso.DocRequestInfo
import at.asitplus.iso.IssuerSignedItem
import at.asitplus.iso.ItemsRequest
import at.asitplus.iso.ItemsRequestList
import at.asitplus.iso.SessionTranscript
import at.asitplus.iso.SingleItemsRequest
import at.asitplus.signum.indispensable.cosef.io.ByteStringWrapper
import at.asitplus.openid.dcql.DCQLClaimsPathPointer
import at.asitplus.openid.dcql.DCQLClaimsQueryList
import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.openid.dcql.DCQLCredentialQueryList
import at.asitplus.openid.dcql.DCQLIsoMdocClaimsQuery
import at.asitplus.openid.dcql.DCQLIsoMdocZkCredentialMetadataAndValidityConstraints
import at.asitplus.openid.dcql.DCQLIsoMdocZkCredentialQuery
import at.asitplus.openid.dcql.DCQLIsoMdocZkSystemSpec
import at.asitplus.openid.dcql.DCQLIsoMdocZkSystemType
import at.asitplus.openid.dcql.DCQLQuery
import at.asitplus.openid.truncateToSeconds
import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.testballoon.matrix.fixture
import at.asitplus.testballoon.matrix.matrixSuite
import at.asitplus.wallet.eupid.EU_PID_DOCTYPE
import at.asitplus.wallet.eupid.EU_PID_METADATA_URL
import at.asitplus.wallet.eupid.EuPidDataElements
import at.asitplus.wallet.eupid.EuPidItemValueSerializerMap
import at.asitplus.wallet.eupid.EuPidJsonValueEncoder
import at.asitplus.wallet.eupid.EuPidMetadataDocument
import at.asitplus.wallet.eupidsdjwt.EU_PID_SD_JWT_METADATA_URL
import at.asitplus.wallet.eupidsdjwt.EuPidSdJwtMetadataDocument
import at.asitplus.wallet.lib.LibraryInitializer
import at.asitplus.wallet.lib.agent.CreatePresentationResult
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.PresentationResponseParameters
import at.asitplus.wallet.lib.agent.RandomSource
import at.asitplus.wallet.lib.agent.toStoreCredentialInput
import at.asitplus.wallet.lib.data.AttributeIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.data.IsoMdocCredentialScheme
import at.asitplus.wallet.lib.data.StaticCredentialMetadataRegistry
import at.asitplus.wallet.lib.data.rfc3986.toUri
import at.asitplus.wallet.mdl.MDL_METADATA_URL
import at.asitplus.wallet.mdl.MobileDrivingLicenceItemValueSerializerMap
import at.asitplus.wallet.mdl.MobileDrivingLicenceJsonValueEncoder
import at.asitplus.wallet.mdl.MobileDrivingLicenceMetadataDocument
import at.asitplus.wallet.sdjwt.SdJwtTypeMetadataDocumentRegistry
import com.benasher44.uuid.uuid4
import io.github.z4kn4fein.semver.Version
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val LONGFELLOW_SYSTEM = "longfellow-libzk-v1"
private const val SINGLE_ATTRIBUTE_SPEC_ID = "my-custom-id-5"
private const val SINGLE_ATTRIBUTE_CIRCUIT_HASH =
    "8d079211715200ff06c5109639245502bfe94aa869908d31176aae4016182121"

val SampleTest by matrixSuite {
    fixture {
        LibraryInitializer.registerCredentialMetadataRegistry(
            StaticCredentialMetadataRegistry(
                documentRegistry = SdJwtTypeMetadataDocumentRegistry(
                    EuPidMetadataDocument,
                ),
                documentUrls = mapOf(
                    EuPidMetadataDocument.first to EU_PID_METADATA_URL,
                )
            )
        )

        LibraryInitializer.registerCredentialSerializers(
            jsonValueEncoder = EuPidJsonValueEncoder,
            itemValueSerializerMap = EuPidItemValueSerializerMap + mapOf(
                EU_PID_DOCTYPE to ((EuPidItemValueSerializerMap[EU_PID_DOCTYPE] ?: emptyMap()) +
                        (EuPidDataElements.GIVEN_NAME to String.serializer()))
            ),
        )

        runBlocking {
            val holderKey = EphemeralKeyWithoutCert()
            val backendRegistry = IsoMdocZkBackendRegistry()
            val holder = HolderAgent(
                keyMaterial = holderKey,
                mdocZkEngine = IsoMdocZkEngine(backendRegistry),
            )
            val givenName = "Susanne"
            val requestedPath = DCQLClaimsPathPointer(EU_PID_DOCTYPE, EuPidDataElements.GIVEN_NAME)
            val credentialScheme = AttributeIndex.resolveIdentifier(EU_PID_DOCTYPE, ISO_MDOC)
                as IsoMdocCredentialScheme

            val zkSystemType = DCQLIsoMdocZkSystemType(
                zkRequired = true,
                systemSpecs = listOf(
                    DCQLIsoMdocZkSystemSpec(
                        id = SINGLE_ATTRIBUTE_SPEC_ID,
                        system = LONGFELLOW_SYSTEM,
                        circuitHash = SINGLE_ATTRIBUTE_CIRCUIT_HASH,
                        numAttributes = 1,
                        version = 7,
                        blockEncHash = 4151,
                        blockEncSig = 4096,
                    )
                ),
            )
            val zkRequest = CredentialPresentationRequest.DCQLRequest(
                DCQLQuery(
                    credentials = DCQLCredentialQueryList(nonEmptyListOf(
                        DCQLIsoMdocZkCredentialQuery(
                            id = DCQLCredentialQueryIdentifier(uuid4().toString()),
                            meta = DCQLIsoMdocZkCredentialMetadataAndValidityConstraints(
                                doctypeValue = EU_PID_DOCTYPE,
                                zkSystemType = zkSystemType,
                            ),
                            claims = DCQLClaimsQueryList(nonEmptyListOf(
                                DCQLIsoMdocClaimsQuery(path = requestedPath)
                            )),
                        )
                    )),
                )
            )
            val deviceRequest = DeviceRequest(
                parsedVersion = Version(1, 0),
                docRequests = arrayOf(
                    DocRequest(
                        itemsRequest = ByteStringWrapper(
                            ItemsRequest(
                                docType = EU_PID_DOCTYPE,
                                namespaces = mapOf(
                                    credentialScheme.isoNamespace to ItemsRequestList(
                                        listOf(SingleItemsRequest(EuPidDataElements.GIVEN_NAME, false))
                                    )
                                ),
                                requestInfo = DocRequestInfo(zkRequest = zkSystemType.toZkRequest()),
                            )
                        )
                    )
                )
            )

            holder.storeCredential(
                IssuerAgent(
                    keyMaterial = EphemeralKeyWithSelfSignedCert(),
                    identifier = "https://issuer.example.com/${uuid4()}".toUri(),
                    randomSource = RandomSource.Default,
                ).issueCredential(
                    CredentialToBeIssued.Iso(
                        issuerSignedItems = listOf(
                            IssuerSignedItem(
                                digestId = 0U,
                                random = ByteArray(16) { it.toByte() },
                                elementIdentifier = EuPidDataElements.GIVEN_NAME,
                                elementValue = givenName,
                            )
                        ),
                        expiration = Clock.System.now().plus(120.minutes).truncateToSeconds(),
                        scheme = credentialScheme,
                        subjectPublicKey = holderKey.publicKey,
                        userInfo = OidcUserInfoExtended.fromOidcUserInfo(
                            OidcUserInfo("subject")
                        ).getOrThrow(),
                    )
                ).getOrThrow().toStoreCredentialInput()
            ).getOrThrow()

            object {
                val holder = holder
                val backendRegistry = backendRegistry
                val zkRequest = zkRequest
                val deviceRequest = deviceRequest
                val givenName = givenName
            }
        }
    } - {
        test("creates a ZK DeviceResponse for one requested attribute") {
            it.backendRegistry.register(LongfellowZkBackend()).getOrThrow()

            val response = it.holder.createDefaultPresentation(
                request = PresentationRequestParameters(
                    nonce = uuid4().toString(),
                    audience = "https://verifier.example.com",
                    calcIsoSessionTranscript = {
                        SessionTranscript.forQr(
                            deviceEngagementBytes = byteArrayOf(),
                            eReaderKeyBytes = byteArrayOf(),
                        )
                    },
                ),
                credentialPresentationRequest = it.zkRequest,
            ).getOrThrow().shouldBeInstanceOf<PresentationResponseParameters.DCQLParameters>()

            val presentation = response.verifiablePresentations.values
                .shouldBeSingleton()
                .single()
                .shouldBeSingleton()
                .single()
                .shouldBeInstanceOf<CreatePresentationResult.DeviceResponse>()
            val deviceResponse = presentation.deviceResponse

            assertZkDeviceResponse(deviceResponse, it.givenName)
        }

        test("creates a ZK DeviceResponse from a DeviceRequest") {
            it.backendRegistry.register(LongfellowZkBackend()).getOrThrow()

            val response = it.holder.createDefaultPresentation(
                request = PresentationRequestParameters(
                    nonce = uuid4().toString(),
                    audience = "https://verifier.example.com",
                    calcIsoSessionTranscript = {
                        SessionTranscript.forQr(
                            deviceEngagementBytes = byteArrayOf(),
                            eReaderKeyBytes = byteArrayOf(),
                        )
                    },
                ),
                credentialPresentationRequest = CredentialPresentationRequest.IsoDeviceRetrieval(
                    it.deviceRequest
                ),
            ).getOrThrow().shouldBeInstanceOf<PresentationResponseParameters.DeviceRetrievalParameters>()

            assertZkDeviceResponse(response.deviceResponse, it.givenName)
        }
    }
}

private fun assertZkDeviceResponse(
    deviceResponse: at.asitplus.iso.DeviceResponse,
    givenName: String,
) {
    deviceResponse.documents.orEmpty().size shouldBe 0
    val zkDocument = deviceResponse.zkDocuments.orEmpty().shouldBeSingleton().single()
    zkDocument.proof.isNotEmpty() shouldBe true
    zkDocument.zkDocumentDataBytes.value.zkSystemId shouldBe SINGLE_ATTRIBUTE_SPEC_ID
    zkDocument.zkDocumentDataBytes.value.issuerSigned
        ?.get(EU_PID_DOCTYPE)
        ?.entries
        ?.shouldBeSingleton()
        ?.single()
        ?.apply {
            elementIdentifier shouldBe EuPidDataElements.GIVEN_NAME
            elementValue.toString() shouldBe givenName
        }
        .shouldNotBeNull()
}
