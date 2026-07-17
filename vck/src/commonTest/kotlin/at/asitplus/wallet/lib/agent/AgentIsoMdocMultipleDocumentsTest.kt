package at.asitplus.wallet.lib.agent

import at.asitplus.data.NonEmptyList.Companion.nonEmptyListOf
import at.asitplus.iso.Document
import at.asitplus.iso.MobileSecurityObject
import at.asitplus.openid.ClaimDescription
import at.asitplus.openid.dcql.DCQLClaimsPathPointer
import at.asitplus.openid.dcql.DCQLClaimsQueryList
import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.openid.dcql.DCQLCredentialQueryList
import at.asitplus.openid.dcql.DCQLIsoMdocClaimsQuery
import at.asitplus.openid.dcql.DCQLIsoMdocCredentialMetadataAndValidityConstraints
import at.asitplus.openid.dcql.DCQLIsoMdocCredentialQuery
import at.asitplus.openid.dcql.DCQLQuery
import at.asitplus.openid.OpenId4VciClaimsPathPointer
import at.asitplus.signum.indispensable.cosef.CoseSigned
import at.asitplus.testballoon.matrix.fixture
import at.asitplus.testballoon.matrix.matrixSuite
import at.asitplus.wallet.lib.cbor.SignCose
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023.CLAIM_FAMILY_NAME
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023.CLAIM_GIVEN_NAME
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.*
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.data.CredentialScheme
import at.asitplus.wallet.lib.data.IsoMdocCredentialScheme
import at.asitplus.wallet.lib.data.SdJwtCredentialScheme
import at.asitplus.wallet.lib.data.VcJwtCredentialScheme
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusValidationResult
import at.asitplus.wallet.lib.data.rfc3986.toUri
import at.asitplus.wallet.lib.randomCwtOrJwtResolver
import com.benasher44.uuid.uuid4
import io.kotest.engine.runBlocking
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldNotBeInstanceOf
import kotlinx.serialization.builtins.ByteArraySerializer

val AgentIsoMdocMultipleDocumentsTest by matrixSuite {

    fixture {
        object {
            val issuerCredentialStore = InMemoryIssuerCredentialStore()
            val issuer = IssuerAgent(
                keyMaterial = EphemeralKeyWithSelfSignedCert(),
                issuerCredentialStore = issuerCredentialStore,
                identifier = "https://issuer.example.com/".toUri(),
                randomSource = RandomSource.Default
            )
            val statusListIssuer = StatusListAgent(issuerCredentialStore = issuerCredentialStore)
            val validator = ValidatorMdoc(
                validator = Validator(tokenStatusResolver = randomCwtOrJwtResolver(statusListIssuer))
            )
            val holderKeyMaterial = EphemeralKeyWithSelfSignedCert()
            val holder = HolderAgent(
                keyMaterial = holderKeyMaterial,
                validatorMdoc = validator,
            ).also {
                runBlocking {
                    it.storeCredential(
                        issuer.issueCredential(
                            DummyCredentialDataProvider.getCredentialForClaim(
                                holderKeyMaterial.publicKey,
                                AtomicAttribute2023,
                                ISO_MDOC,
                                ClaimToBeIssued(CLAIM_GIVEN_NAME, "Susanne"),
                            ).getOrThrow()
                        ).getOrThrow().toStoreCredentialInput()
                    ).getOrThrow()
                    it.storeCredential(
                        issuer.issueCredential(
                            DummyCredentialDataProvider.getCredentialForClaim(
                                holderKeyMaterial.publicKey,
                                AtomicAttribute2025,
                                ISO_MDOC,
                                ClaimToBeIssued(CLAIM_FAMILY_NAME, "Meier"),
                            ).getOrThrow()
                        ).getOrThrow().toStoreCredentialInput()
                    ).getOrThrow()
                }
            }
            val verifierId = "urn:${uuid4()}"
            val verifier = NonceChallengeVerifier(
                verifierId = verifierId,
                verifier = VerifierAgent(
                    identifier = verifierId,
                    validatorMdoc = validator,
                ),
            )
            val signer = SignCose<ByteArray>(keyMaterial = holderKeyMaterial)
        }
    } - {

        test("dcql: multiple credentials should be multiple device responses for remote presentation") {
            val request = it.verifier.createPresentationRequest(
                calcIsoDeviceSignaturePlain = simpleSigner(it.signer),
            )
            val presentationParameters = it.holder.createDefaultPresentation(
                request = request,
                credentialPresentationRequest = CredentialPresentationRequest.DCQLRequest(
                    DCQLQuery(
                        credentials = DCQLCredentialQueryList(
                            isoMdocCredentialQuery(AtomicAttribute2023, CLAIM_GIVEN_NAME),
                            isoMdocCredentialQuery(AtomicAttribute2025, CLAIM_FAMILY_NAME),
                        )
                    )
                )
            ).getOrThrow().shouldBeInstanceOf<PresentationResponseParameters.DCQLParameters>()

            val presentationResults = presentationParameters.verifiablePresentations.values.flatten()
            presentationResults.shouldHaveSize(2).forEach { result ->
                result.shouldBeInstanceOf<CreatePresentationResult.DeviceResponse>()
                it.verifier.verifyPresentationIsoMdoc(result.deviceResponse, documentVerifier()).getOrThrow()
                    .shouldBeInstanceOf<Verifier.VerifyPresentationResult.SuccessIso>().apply {
                        documents.shouldBeSingleton().forEach {
                            it.freshnessSummary.tokenStatusValidationResult
                                .shouldNotBeInstanceOf<TokenStatusValidationResult.Invalid>()
                        }
                    }
            }
            val validItems = presentationResults
                .filterIsInstance<CreatePresentationResult.DeviceResponse>()
                .map { resp ->
                    it.verifier.verifyPresentationIsoMdoc(resp.deviceResponse, documentVerifier()).getOrThrow()
                }
                .flatMap { it.shouldBeInstanceOf<Verifier.VerifyPresentationResult.SuccessIso>().documents }
                .flatMap { it.validItems }
            validItems.firstOrNull { item -> item.elementIdentifier == CLAIM_GIVEN_NAME }
                .shouldNotBeNull().elementValue shouldBe "Susanne"
            validItems.firstOrNull { item -> item.elementIdentifier == CLAIM_FAMILY_NAME }
                .shouldNotBeNull().elementValue shouldBe "Meier"
        }
    }
}

private fun isoMdocCredentialQuery(
    scheme: CredentialScheme,
    claim: String,
) = DCQLIsoMdocCredentialQuery(
    id = DCQLCredentialQueryIdentifier(uuid4().toString()),
    meta = DCQLIsoMdocCredentialMetadataAndValidityConstraints(
        doctypeValue = scheme.isoDocType!!,
    ),
    claims = DCQLClaimsQueryList(
        nonEmptyListOf(
            DCQLIsoMdocClaimsQuery(
                path = DCQLClaimsPathPointer(scheme.isoNamespace!!, claim)
            )
        )
    ),
)

private fun simpleSigner(
    signer: SignCose<ByteArray>
): suspend (IsoDeviceSignatureInput) -> CoseSigned<ByteArray>? = { input ->
    signer(
        protectedHeader = null,
        unprotectedHeader = null,
        payload = input.docType.encodeToByteArray(),
        serializer = ByteArraySerializer()
    ).getOrThrow()
}

// No OpenID4VP, no need to verify the device signature
private fun documentVerifier(): suspend (MobileSecurityObject, Document) -> Boolean = { _, _ -> true }


object AtomicAttribute2025 : CredentialScheme, IsoMdocCredentialScheme, SdJwtCredentialScheme, VcJwtCredentialScheme {
    const val CLAIM_GIVEN_NAME = "given_name"
    const val CLAIM_FAMILY_NAME = "family_name"
    const val CLAIM_DATE_OF_BIRTH = "date_of_birth"
    const val CLAIM_PORTRAIT = "portrait"
    val schemaUri: String = "https://wallet.a-sit.at/schemas/1.0.0/AtomicAttribute2025.json"
    override val vcType: String = "AtomicAttribute2025"
    override val sdJwtType: String = "AtomicAttribute2025"
    override val isoNamespace: String = "at.a-sit.wallet.atomic-attribute-2025"
    override val isoDocType: String = "at.a-sit.wallet.atomic-attribute-2025.iso"
    override val claimDescriptions: Set<ClaimDescription>
        get() = setOf(
            ClaimDescription(OpenId4VciClaimsPathPointer(CLAIM_GIVEN_NAME)),
            ClaimDescription(OpenId4VciClaimsPathPointer(CLAIM_FAMILY_NAME)),
            ClaimDescription(OpenId4VciClaimsPathPointer(CLAIM_DATE_OF_BIRTH)),
            ClaimDescription(OpenId4VciClaimsPathPointer(CLAIM_PORTRAIT)),
        )
    override val supportedRepresentations: Collection<at.asitplus.wallet.lib.data.CredentialRepresentation>
        get() = listOf(ISO_MDOC, PLAIN_JWT, SD_JWT)
}
