package at.asitplus.wallet.lib.agent

import at.asitplus.catching
import at.asitplus.openid.CredentialFormatEnum
import at.asitplus.openid.dcql.DCQLCredentialQuery
import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.openid.dcql.DCQLCredentialQueryList
import at.asitplus.openid.dcql.DCQLIsoMdocCredentialMetadataAndValidityConstraints
import at.asitplus.openid.dcql.DCQLQuery
import at.asitplus.testballoon.matrix.matrixSuite
import at.asitplus.wallet.lib.data.ConstantIndex.AtomicAttribute2023
import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.ISO_MDOC
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.data.CredentialScheme
import at.asitplus.wallet.lib.data.rfc3986.toUri
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Regression test: ISO mdoc store entries serialized before [SubjectCredentialStore.StoreEntry.schemeIdentifier]
 * existed keep that field `null`. Input-descriptor matching must still derive the docType from `issuerAuth` so that
 * `MSO_MDOC` input descriptors keyed by docType continue to match those legacy entries.
 */
val LegacyIsoSchemeMatchingTest by matrixSuite {

    suspend fun legacyIsoEntryWithoutSchemeIdentifier(): SubjectCredentialStore.StoreEntry.Iso {
        val holderKeyMaterial = EphemeralKeyWithSelfSignedCert()
        val issuer = IssuerAgent(
            keyMaterial = EphemeralKeyWithSelfSignedCert(),
            identifier = "https://issuer.example.com/".toUri(),
            randomSource = RandomSource.Default,
        )
        val issued = issuer.issueCredential(
            DummyCredentialDataProvider.getCredential(holderKeyMaterial.publicKey, AtomicAttribute2023, ISO_MDOC)
                .getOrThrow()
        ).getOrThrow().shouldBeInstanceOf<Issuer.IssuedCredential.Iso>()

        @Suppress("DEPRECATION")
        return SubjectCredentialStore.StoreEntry.Iso(
            issuerSigned = issued.issuerSigned,
            schemeIdentifier = null, // entry serialized before scheme-identifier was introduced
        )
    }

    fun dcqlRequest(docType: String) = CredentialPresentationRequest.DCQLRequest(
        DCQLQuery(
            credentials = DCQLCredentialQueryList(
                DCQLCredentialQuery(
                    id = DCQLCredentialQueryIdentifier("credential"),
                    format = CredentialFormatEnum.MSO_MDOC,
                    meta = DCQLIsoMdocCredentialMetadataAndValidityConstraints(doctypeValue = docType),
                )
            )
        )
    )

    fun readOnlyStore(entry: SubjectCredentialStore.StoreEntry) =
        object : SubjectCredentialStore by InMemorySubjectCredentialStore() {
            override suspend fun getCredentials(credentialSchemes: Collection<CredentialScheme>?) = catching {
                listOf(entry)
            }
        }

    "legacy ISO entry without scheme identifier matches its docType input descriptor" {
        val entry = legacyIsoEntryWithoutSchemeIdentifier()
        val holder = HolderAgent(EphemeralKeyWithSelfSignedCert(), readOnlyStore(entry))

        holder
            .matchPresentationRequestAgainstCredentialStore(dcqlRequest(AtomicAttribute2023.isoDocType))
            .getOrThrow()
            .shouldBeInstanceOf<DCQLMatchingResult<*>>()
            .matchingResult.credentialQueryMatches.values.single().size shouldBe 1
    }

    "legacy ISO entry without scheme identifier is rejected for a mismatched docType" {
        val entry = legacyIsoEntryWithoutSchemeIdentifier()
        val holder = HolderAgent(EphemeralKeyWithSelfSignedCert(), readOnlyStore(entry))

        holder.matchPresentationRequestAgainstCredentialStore(dcqlRequest("org.example.other.doctype"))
            .getOrThrow()
            .shouldBeInstanceOf<DCQLMatchingResult<*>>()
            .matchingResult.credentialQueryMatches.values.single().size shouldBe 0
    }
}
