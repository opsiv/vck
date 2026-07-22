package at.asitplus.wallet.lib.agent

import at.asitplus.catchingUnwrapped
import at.asitplus.iso.DeviceAuth
import at.asitplus.iso.DeviceNameSpaces
import at.asitplus.iso.DeviceSigned
import at.asitplus.iso.Document
import at.asitplus.signum.indispensable.Digest
import at.asitplus.signum.indispensable.cosef.CoseKey
import at.asitplus.signum.indispensable.cosef.io.ByteStringWrapper
import at.asitplus.signum.indispensable.cosef.toCoseKey
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.testballoon.matrix.matrixSuite
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusValidationResult
import at.asitplus.wallet.lib.data.rfc3986.toUri
import at.asitplus.wallet.lib.randomCwtOrJwtResolver
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldNotBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private data class Config(
    val issuer: Issuer,
    val statusListIssuer: StatusListIssuer,
    val issuerCredentialStore: InMemoryIssuerCredentialStore,
    val issuerKeyMaterial: KeyMaterial,
    val verifierKeyMaterial: KeyMaterial,
    val validator: ValidatorMdoc
) {

    companion object {
        fun random(): Config {
            val issuerKeyMaterial = EphemeralKeyWithSelfSignedCert()
            val issuerCredentialStore = InMemoryIssuerCredentialStore()
            val statusListIssuer = StatusListAgent(issuerCredentialStore = issuerCredentialStore)
            return Config(
                validator = ValidatorMdoc(
                    validator = Validator(
                        tokenStatusResolver = randomCwtOrJwtResolver(statusListIssuer)
                    )
                ),
                issuerCredentialStore = issuerCredentialStore,
                issuerKeyMaterial = issuerKeyMaterial,
                issuer = IssuerAgent(
                    keyMaterial = issuerKeyMaterial,
                    issuerCredentialStore = issuerCredentialStore,
                    identifier = "https://issuer.example.com/".toUri(),
                    randomSource = RandomSource.Default
                ),
                statusListIssuer = statusListIssuer,
                verifierKeyMaterial = EphemeralKeyWithoutCert()
            )
        }
    }
}

val ValidatorMdocTest by matrixSuite {
    with(Config.random()) {
        "credentials are valid for" {
            val credential = issuer.issueCredential(
                DummyCredentialDataProvider.getCredential(
                    verifierKeyMaterial.publicKey,
                    ConstantIndex.AtomicAttribute2023,
                    ISO_MDOC,
                ).getOrThrow()
            ).getOrThrow()
                .shouldBeInstanceOf<Issuer.IssuedCredential.Iso>().apply {
                    // Assert the issuanceOffset in IssuerAgent
                    issuerSigned.issuerAuth.payload.shouldNotBeNull().apply {
                        validityInfo.validFrom shouldBeLessThan Clock.System.now().minus(1.minutes)
                        validityInfo.validFrom shouldNotBeGreaterThan Clock.System.now()
                    }
                }

            val issuerKey: CoseKey? =
                credential.issuerSigned.issuerAuth.unprotectedHeader?.certificateChain?.firstOrNull()?.let {
                    catchingUnwrapped { X509Certificate.decodeFromDer(it) }.getOrNull()?.decodedPublicKey?.getOrNull()
                        ?.toCoseKey()
                        ?.getOrNull()
                }

            validator.verifyIsoCred(credential.issuerSigned, issuerKey).getOrThrow()
                .shouldBeInstanceOf<Verifier.VerifyCredentialResult.SuccessIso>()
        }
    }
    with(Config.random()) {
        "revoked credentials are not valid" {
            val credential = issuer.issueCredential(
                DummyCredentialDataProvider.getCredential(
                    verifierKeyMaterial.publicKey,
                    ConstantIndex.AtomicAttribute2023,
                    ISO_MDOC,
                ).getOrThrow()
            ).getOrThrow()
                .shouldBeInstanceOf<Issuer.IssuedCredential.Iso>()

            val issuerKey: CoseKey? =
                credential.issuerSigned.issuerAuth.unprotectedHeader?.certificateChain?.firstOrNull()?.let {
                    catchingUnwrapped { X509Certificate.decodeFromDer(it) }.getOrNull()?.decodedPublicKey?.getOrNull()
                        ?.toCoseKey()
                        ?.getOrNull()
                }

            val value = validator.verifyIsoCred(credential.issuerSigned, issuerKey).getOrThrow()
                .shouldBeInstanceOf<Verifier.VerifyCredentialResult.SuccessIso>()
            issuerCredentialStore.setStatus(
                timePeriod = FixedTimePeriodProvider.timePeriod,
                index = credential.issuerSigned.issuerAuth.payload.shouldNotBeNull()
                    .status.shouldBeInstanceOf<StatusListInfo>().index,
                status = TokenStatus.Invalid,
            ) shouldBe true
            validator.checkRevocationStatus(value.issuerSigned)
                .shouldBeInstanceOf<TokenStatusValidationResult.Invalid>()
        }
    }
    with(Config.random()) {
        "document errors are preserved in parsed output" {
            val credential = issuer.issueCredential(
                DummyCredentialDataProvider.getCredential(
                    verifierKeyMaterial.publicKey,
                    ConstantIndex.AtomicAttribute2023,
                    ISO_MDOC,
                ).getOrThrow().shouldBeInstanceOf<CredentialToBeIssued.Iso>()
                    .copy(digest = Digest.SHA384)
            ).getOrThrow()
                .shouldBeInstanceOf<Issuer.IssuedCredential.Iso>()

            val documentErrors = mapOf(
                ConstantIndex.AtomicAttribute2023.isoNamespace to mapOf(
                    ConstantIndex.AtomicAttribute2023.CLAIM_GIVEN_NAME to 42,
                ),
            )

            val document = Document(
                docType = ConstantIndex.AtomicAttribute2023.isoDocType,
                issuerSigned = credential.issuerSigned,
                deviceSigned = DeviceSigned(
                    namespaces = ByteStringWrapper(DeviceNameSpaces(mapOf())),
                    deviceAuth = DeviceAuth(),
                ),
                errors = documentErrors,
            )

            val parsed = validator.verifyDocument(document) { _, _ -> true }

            parsed.mso.digest shouldBe Digest.SHA384
            parsed.mso.valueDigests.values.single().entries.all { it.value.size == 48 } shouldBe true
            parsed.validItems.size shouldBe 4
            parsed.invalidItems shouldBe emptyList()
            parsed.documentErrors shouldBe documentErrors
        }
    }
}
