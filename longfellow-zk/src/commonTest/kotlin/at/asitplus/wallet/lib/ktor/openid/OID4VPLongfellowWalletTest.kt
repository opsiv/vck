//package at.asitplus.wallet.lib.ktor.openid
//
//import kotlin.time.TimeSource
//import kotlin.time.Duration
//import at.asitplus.data.NonEmptyList.Companion.nonEmptyListOf
//import at.asitplus.iso.IssuerSignedItem
//import at.asitplus.iso.Item
//import at.asitplus.openid.OidcUserInfo
//import at.asitplus.openid.OidcUserInfoExtended
//import at.asitplus.openid.OpenIdConstants.ResponseMode
//import at.asitplus.openid.RequestObjectParameters
//import at.asitplus.openid.dcql.DCQLZkSystemType
//import at.asitplus.openid.truncateToSeconds
//import at.asitplus.testballoon.withFixtureGenerator
//import at.asitplus.wallet.lib.agent.ClaimToBeIssued
//import at.asitplus.wallet.lib.agent.CredentialToBeIssued
//import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
//import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
//import at.asitplus.wallet.lib.agent.HolderAgent
//import at.asitplus.wallet.lib.agent.IssuerAgent
//import at.asitplus.wallet.lib.agent.RandomSource
//import at.asitplus.wallet.lib.agent.toStoreCredentialInput
//import at.asitplus.wallet.lib.data.ConstantIndex
//import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
//import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
//import at.asitplus.wallet.lib.data.SelectiveDisclosureItem
//import at.asitplus.wallet.lib.data.rfc3986.toUri
//import at.asitplus.wallet.lib.extensions.supportedSdAlgorithms
//import at.asitplus.wallet.lib.oidvci.decodeFromPostBody
//import at.asitplus.wallet.lib.openid.AuthnResponseResult
//import at.asitplus.wallet.lib.openid.AuthnResponseResult.SuccessIso
//import at.asitplus.wallet.lib.openid.AuthnResponseResult.SuccessSdJwt
//import at.asitplus.wallet.lib.openid.ClientIdScheme
//import at.asitplus.wallet.lib.openid.OpenId4VpVerifier
//import at.asitplus.wallet.lib.openid.OpenId4VpVerifier.CreationOptions
//import at.asitplus.wallet.lib.openid.PresentationMechanismEnum
//import at.asitplus.wallet.lib.openid.RequestOptions
//import at.asitplus.wallet.lib.openid.RequestOptionsCredential
//import at.asitplus.wallet.mdl.MobileDrivingLicenceDataElements
//import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
//import com.benasher44.uuid.uuid4
//import de.infix.testBalloon.framework.core.testSuite
//import io.github.aakira.napier.Napier
//import io.kotest.assertions.throwables.shouldNotThrowAny
//import io.kotest.matchers.nulls.shouldNotBeNull
//import io.kotest.matchers.types.shouldBeInstanceOf
//import io.ktor.client.*
//import io.ktor.client.engine.*
//import io.ktor.client.engine.mock.*
//import io.ktor.client.request.*
//import io.ktor.http.*
//import io.ktor.util.toMap
//import io.matthewnelson.encoding.base16.Base16
//import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.sync.Mutex
//import kotlinx.coroutines.withContext
//import kotlinx.coroutines.withTimeout
//import kotlinx.serialization.json.jsonPrimitive
//import kotlin.random.Random
//import kotlin.time.Clock
//import kotlin.time.Duration.Companion.minutes
//
//
//val OID4VPLongfellowWalletTest by testSuite {
//
//    withFixtureGenerator {
//        object {
//            val countdownLatch = Mutex(true)
//            val keyMaterial = EphemeralKeyWithoutCert()
//            val holderAgent = HolderAgent(keyMaterial)
//            lateinit var wallet: OpenId4VpWallet
//            lateinit var url: String
//            lateinit var mockEngine: HttpClientEngine
//
//            fun setupWallet(engine: HttpClientEngine) = OpenId4VpWallet(
//                engine = engine,
//                keyMaterial = keyMaterial,
//                holderAgent = holderAgent,
//            ).also { this.wallet = it }
//
//            suspend fun storeMockCredentials(
//                scheme: ConstantIndex.CredentialScheme,
//                representation: ConstantIndex.CredentialRepresentation,
//                attributes: Map<String, Any>,
//            ) = holderAgent.storeCredential(
//                IssuerAgent(
//                    keyMaterial = EphemeralKeyWithSelfSignedCert(),
//                    identifier = "https://issuer.example.com/".toUri(),
//                    randomSource = RandomSource.Default
//                ).issueCredential(
//                    representation.toCredentialToBeIssued(scheme, attributes)
//                ).getOrThrow().toStoreCredentialInput()
//            ).getOrThrow()
//
//            fun ConstantIndex.CredentialRepresentation.toCredentialToBeIssued(
//                scheme: ConstantIndex.CredentialScheme,
//                attributes: Map<String, Any>,
//            ): CredentialToBeIssued = when (this) {
//                SD_JWT -> CredentialToBeIssued.VcSd(
//                    claims = attributes.map { it.toClaimToBeIssued() },
//                    expiration = Clock.System.now().plus(2.minutes).truncateToSeconds(),
//                    scheme = scheme,
//                    subjectPublicKey = keyMaterial.publicKey,
//                    userInfo = OidcUserInfoExtended.fromOidcUserInfo(OidcUserInfo("subject")).getOrThrow(),
//                    sdAlgorithm = supportedSdAlgorithms.random()
//                )
//
//                ISO_MDOC -> CredentialToBeIssued.Iso(
//                    issuerSignedItems = attributes.entries.mapIndexed { index, entry ->
//                        entry.toIssuerSignedItem(index.toUInt())
//                    },
//                    expiration = Clock.System.now().plus(2.minutes).truncateToSeconds(),
//                    scheme = scheme,
//                    subjectPublicKey = keyMaterial.publicKey,
//                    userInfo = OidcUserInfoExtended.fromOidcUserInfo(OidcUserInfo("subject")).getOrThrow(),
//                )
//
//                else -> TODO()
//            }
//
//            /**
//             * Setup the mock relying party service, for getting requests (referenced by `request_uri`) and to decode posted
//             * authentication responses
//             */
//            suspend fun setupRelyingPartyService(
//                clientId: String,
//                requestOptions: RequestOptions,
//                validate: (AuthnResponseResult) -> Unit,
//            ) {
//                val requestEndpointPath = "/request/${uuid4()}"
//                val redirectUri = "http://rp.example.com/cb"
//                val verifier = OpenId4VpVerifier(
//                    clientIdScheme = ClientIdScheme.PreRegistered(clientId, redirectUri),
//                )
//                val responseEndpointPath = "/response"
//                val (url, jar) = verifier.createAuthnRequest(
//                    requestOptions.copy(responseUrl = responseEndpointPath),
//                    CreationOptions.SignedRequestByReference(
//                        "http://wallet.example.com/",
//                        "http://rp.example.com$requestEndpointPath"
//                    )
//                ).getOrThrow()
//                jar.shouldNotBeNull()
//
//                this.mockEngine = MockEngine { request ->
//                    when {
//                        request.url.fullPath.startsWith(requestEndpointPath) -> {
//                            val requestBody = request.body.toByteArray().decodeToString()
//                            val queryParameters: Map<String, String> =
//                                request.url.parameters.toMap().entries.associate { it.key to it.value.first() }
//                            val requestObjectParameters = if (requestBody.isNotEmpty())
//                                requestBody.decodeFromPostBody<RequestObjectParameters>()
//                            else RequestObjectParameters(
//                                walletMetadataString = queryParameters["wallet_metadata"],
//                                walletNonce = queryParameters["wallet_nonce"]
//                            )
//                            respond(jar.invoke(requestObjectParameters).getOrThrow())
//                        }
//
//                        request.url.fullPath.startsWith(responseEndpointPath) or request.url.toString()
//                            .startsWith(redirectUri) -> {
//                            val requestBody = request.body.toByteArray().decodeToString()
//                            val time = measureTime { val result =
//                                if (requestBody.isNotEmpty()) verifier.validateAuthnResponse(requestBody)
//                                else verifier.validateAuthnResponse(request.url.toString())
//                                validate(result) }
//                            Napier.i("Credential Verification Time: $time")
//                            respondOk()
//                        }
//
//                        else -> respondError(HttpStatusCode.NotFound)
//                            .also { Napier.w("NOT MATCHED ${request.url.fullPath}") }
//                    }
//                }
//                this.url = url
//            }
//        }
//    } - {
//        test("Selective Disclosure with mDL and ZK") {
//            val scheme = MobileDrivingLicenceScheme
//            val representation = ISO_MDOC
//            val requestedClaims = setOf(
//                MobileDrivingLicenceDataElements.FAMILY_NAME,
//                MobileDrivingLicenceDataElements.GIVEN_NAME,
//                // TODO: more potential attributes here
//            )
//            val attributes = requestedClaims.associateWith { randomString().take(15) }
//
//            it.storeMockCredentials(scheme, representation, attributes)
//
//            val responseMode = ResponseMode.Query
//            val clientId = uuid4().toString()
//            val requestOptions = RequestOptions(
//                credentials = setOf(
//                    RequestOptionsCredential(
//                        credentialScheme = MobileDrivingLicenceScheme,
//                        representation = ISO_MDOC,
//                        requestedAttributes = requestedClaims,
//                        // comment out   ZkSystemTypes to run in non-ZK mode
//                        zkSystemTypes = nonEmptyListOf(
//                            DCQLZkSystemType(
//                                id = "test123",
//                                system = "longfellow-libzk-v1",
//                                circuitHash = "b4bb6f01b7043f4f51d8302a30b36e3d4d2d0efc3c24557ab9212ad524a9764e",
//                                numAttributes = requestedClaims.size,
//                                version = 6,
//                            )
//                        )
//                    )
//                ),
//                presentationMechanism = PresentationMechanismEnum.DCQL,
//                responseMode = responseMode,
//            )
//            it.setupRelyingPartyService(clientId, requestOptions) { result ->
//                if (result.containsAllAttributes(attributes)) {
//                    it.countdownLatch.unlock()
//                }
//            }
//            it.setupWallet(it.mockEngine)
//
//            val preparationState = it.wallet.startAuthorizationResponsePreparation(it.url).getOrThrow()
//            shouldNotThrowAny { it.wallet.getMatchingCredentials(preparationState).getOrThrow() }
//
//            val time = measureTime {
//                it.wallet.finalizeAuthorizationResponse(preparationState).getOrThrow()
//                    .shouldBeInstanceOf<OpenId4VpWallet.AuthenticationSuccess>()
//                    .redirectUri?.let { uri -> HttpClient(it.mockEngine).get(uri) }
//            }
//            Napier.i("Credential Proof Generation Time: $time")
//
//
//            assertPresentation(it.countdownLatch)
//        }
//
//    }
//}
//
//private fun Map.Entry<String, Any>.toClaimToBeIssued(): ClaimToBeIssued = ClaimToBeIssued(key, value)
//
//private fun Map.Entry<String, Any>.toIssuerSignedItem(digestId: UInt = 0U): IssuerSignedItem =
//    IssuerSignedItem(digestId, Random.nextBytes(16), key, value)
//
//private fun AuthnResponseResult.containsAllAttributes(expectedAttributes: Map<String, String>): Boolean =
//    when (this) {
//        is SuccessSdJwt -> this.containsAllAttributes(expectedAttributes)
//        is SuccessIso -> this.containsAllAttributes(expectedAttributes)
//        is AuthnResponseResult.VerifiableDCQLPresentationValidationResults ->
//            this.validationResults.values.any { it.containsAllAttributes(expectedAttributes) }
//        else -> false
//    }
//
//private fun SuccessSdJwt.containsAllAttributes(attributes: Map<String, String>): Boolean =
//    attributes.all { containsAttribute(it) }
//
//private fun SuccessSdJwt.containsAttribute(attribute: Map.Entry<String, String>): Boolean =
//    disclosures.toList().any { it.matchesAttribute(attribute) }
//
//private fun SuccessIso.containsAllAttributes(attributes: Map<String, String>): Boolean =
//    attributes.all { containsAttribute(it) }
//
//private fun SuccessIso.containsAttribute(attribute: Map.Entry<String, String>): Boolean =
//    documents.any { doc -> doc.validItems.any { it.matchesAttribute(attribute) } }
//
//private fun SelectiveDisclosureItem.matchesAttribute(attribute: Map.Entry<String, String>): Boolean =
//    claimName == attribute.key && claimValue.jsonPrimitive.content == attribute.value
//
//private fun Item.matchesAttribute(attribute: Map.Entry<String, String>): Boolean =
//    elementIdentifier == attribute.key && elementValue.toString() == attribute.value
//
//// If the countdownLatch has been unlocked, the correct credential has been posted to the RP, and we're done!
//private suspend fun assertPresentation(countdownLatch: Mutex) {
//    withContext(Dispatchers.Default.limitedParallelism(1)) {
//        withTimeout(5000) {
//            countdownLatch.lock()
//        }
//    }
//}
//
//
//private fun randomString(): String = Random.nextBytes(32).encodeToString(Base16)
//inline fun measureTime(block: () -> Unit): Duration {
//    val mark = TimeSource.Monotonic.markNow()
//    block()
//    return mark.elapsedNow()
//}