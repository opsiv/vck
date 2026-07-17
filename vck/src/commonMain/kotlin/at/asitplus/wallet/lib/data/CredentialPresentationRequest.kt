package at.asitplus.wallet.lib.data

import at.asitplus.dif.Constraint
import at.asitplus.dif.ConstraintField
import at.asitplus.dif.DifInputDescriptor
import at.asitplus.dif.FormatHolder
import at.asitplus.dif.PresentationDefinition
import at.asitplus.iso.DeviceRequest
import at.asitplus.iso.Document
import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.openid.dcql.DCQLCredentialSubmissionOption
import at.asitplus.openid.dcql.DCQLQuery
import at.asitplus.wallet.lib.agent.PresentationExchangeCredentialDisclosure
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Encodes the requirements of a credential presentation, created from a verifier.
 */
@Serializable(with = CredentialPresentationRequestSerializer::class)
sealed interface CredentialPresentationRequest {

    /** Creates the actual presentation of credentials, with no selection by the user. */
    fun toCredentialPresentation(): CredentialPresentation

    /** Presentation Exchange, formerly used by OpenID4VP, now deprecated. */
    @Suppress("DEPRECATION")
    @Deprecated("Support for Presentation Exchange been removed from OpenID4VP")
    @Serializable
    data class PresentationExchangeRequest(
        val presentationDefinition: PresentationDefinition,
        val fallbackFormatHolder: FormatHolder? = null,
    ) : CredentialPresentationRequest {
        override fun toCredentialPresentation() = toCredentialPresentation(null)

        fun toCredentialPresentation(
            inputDescriptorSubmissions: Map<String, PresentationExchangeCredentialDisclosure<SubjectCredentialStore.StoreEntry>>?
        ): CredentialPresentation = CredentialPresentation.PresentationExchangePresentation(
            presentationRequest = this,
            inputDescriptorSubmissions = inputDescriptorSubmissions
        )

        companion object {
            @Deprecated("Support for Presentation Exchange been removed from OpenID4VP")
            fun forAttributeNames(vararg attributeName: String) = PresentationExchangeRequest(
                PresentationDefinition(
                    DifInputDescriptor(
                        Constraint(
                            fields = attributeName.map { ConstraintField(path = listOf(it)) }.toSet()
                        )
                    )
                ),
            )
        }
    }

    /** DCQL, used by OpenID4VP */
    @Serializable
    @JvmInline
    value class DCQLRequest(
        val dcqlQuery: DCQLQuery
    ) : CredentialPresentationRequest {
        override fun toCredentialPresentation() = toCredentialPresentation(null)

        fun toCredentialPresentation(
            credentialQuerySubmissions: Map<DCQLCredentialQueryIdentifier, List<DCQLCredentialSubmissionOption<SubjectCredentialStore.StoreEntry>>>?
        ): CredentialPresentation = CredentialPresentation.DCQLPresentation(
            presentationRequest = this,
            credentialQuerySubmissions = credentialQuerySubmissions
        )

    }
}