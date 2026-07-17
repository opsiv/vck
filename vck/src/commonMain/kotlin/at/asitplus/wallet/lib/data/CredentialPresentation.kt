package at.asitplus.wallet.lib.data

import at.asitplus.iso.Document
import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.openid.dcql.DCQLCredentialSubmissionOption
import at.asitplus.wallet.lib.agent.PresentationExchangeCredentialDisclosure
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import kotlinx.serialization.Serializable

/**
 * The credentials that are actually being used by the holder to create the verifiable presentation,
 * to fulfill a [CredentialPresentationRequest] from the verifier.
 */
@Serializable
sealed interface CredentialPresentation {

    /** The request from the verifier */
    val presentationRequest: CredentialPresentationRequest

    @Deprecated("Support for Presentation Exchange been removed from OpenID4VP")
    @Serializable
    data class PresentationExchangePresentation(
        override val presentationRequest: CredentialPresentationRequest.PresentationExchangeRequest,
        val inputDescriptorSubmissions: Map<String, PresentationExchangeCredentialDisclosure<SubjectCredentialStore.StoreEntry>>? = null
    ) : CredentialPresentation

    /** DCQL, used by OpenID4VP */
    @Serializable
    data class DCQLPresentation(
        override val presentationRequest: CredentialPresentationRequest.DCQLRequest,
        val credentialQuerySubmissions: Map<DCQLCredentialQueryIdentifier, List<DCQLCredentialSubmissionOption<SubjectCredentialStore.StoreEntry>>>?,
    ) : CredentialPresentation
}