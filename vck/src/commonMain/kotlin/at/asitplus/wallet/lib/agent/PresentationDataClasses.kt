package at.asitplus.wallet.lib.agent

import at.asitplus.dif.PresentationSubmission
import at.asitplus.iso.DeviceNameSpaces
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.openid.TransactionDataBase64Url
import at.asitplus.openid.dcql.DCQLCredentialQueryIdentifier
import at.asitplus.signum.indispensable.Digest
import at.asitplus.signum.indispensable.cosef.CoseSigned
import at.asitplus.signum.indispensable.cosef.io.ByteStringWrapper
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.signum.indispensable.io.Base64UrlStrict
import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.lib.data.VerifiablePresentationJws
import at.asitplus.wallet.lib.jws.SdJwtSigned
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

/**
 * Input to create a verifiable presentation of credentials, i.e. contains input required to fill fields in the VP,
 * like a challenge from the verifier and their identifier.
 *
 * Decouples the reading of these data fields from the protocol input (e.g., OpenID4VP) from their usage.
 *
 * See [VerifiablePresentationFactory.createVerifiablePresentation] for usage of these data fields.
 */
data class PresentationRequestParameters(
    val nonce: String,
    val audience: String,
    val transactionData: List<TransactionDataBase64Url>? = null,
    /**
     * Handle calculating device signature for ISO mDocs, as this depends on the transport protocol
     * (OpenID4VP with ISO/IEC 18013-7)
     */
    val calcIsoDeviceSignaturePlain: (suspend (input: IsoDeviceSignatureInput) -> CoseSigned<ByteArray>?) = { null },
    @Deprecated("This value is given by `DCQLCredentialQuery.multiple` or by the request being ISO Device Retrieval")
    val returnOneDeviceResponse: Boolean = false
) {
    /**
     * According to OID4VP 1.0 B3.3.1 every TransactionData entry may define different Digest algorithms
     * however in the [at.asitplus.wallet.lib.data.KeyBindingJws] we are only allowed to specify one.
     * To remedy this we only look at the intersection of all sets;
     * if empty OID4VP 1.0 requires that every party must support [Digest.SHA256].
     *
     * For convenience, we always select the first if the set is non-empty
     */
    val transactionDataHashesAlgorithm: Digest? = getCommonHashesAlgorithms(transactionData)?.first().toDigest()
}

data class IsoDeviceSignatureInput(
    val docType: String,
    val deviceNameSpaceBytes: ByteStringWrapper<DeviceNameSpaces>,
)

// TODO Better documentation
/** Intermediate classes for the response of the presentation protocol */
sealed interface PresentationResponseParameters {
    // TODO Move to vck-openid because it's used only there?
    val vpToken: JsonElement?

    @Deprecated("Presentation Exchange is deprecated, use DCQL or DeviceRequest instead")
    val presentationSubmission: PresentationSubmission?

    data class DCQLParameters(
        val verifiablePresentations: Map<DCQLCredentialQueryIdentifier, List<CreatePresentationResult>>,
    ) : PresentationResponseParameters {
        override val vpToken
            get() = buildJsonObject {
                verifiablePresentations.entries.forEach {
                    putJsonArray(it.key.string) {
                        it.value.forEach {
                            add(it.toJsonPrimitive())
                        }
                    }
                }
            }

        override val presentationSubmission
            get() = null
    }

    data class PresentationExchangeParameters(
        val presentationResults: List<CreatePresentationResult>,
        override val presentationSubmission: PresentationSubmission,
    ) : PresentationResponseParameters {
        override val vpToken = presentationResults.map {
            it.toJsonPrimitive()
        }.singleOrArray()

        private fun List<JsonPrimitive>.singleOrArray() = if (size == 1) {
            this[0]
        } else buildJsonArray {
            forEach { add(it) }
        }
    }

    companion object {
        private fun CreatePresentationResult.toJsonPrimitive() = when (val presentationResult = this) {
            is CreatePresentationResult.VpJws -> JsonPrimitive(presentationResult.serialized)
            is CreatePresentationResult.VcJws -> JsonPrimitive(presentationResult.serialized)
            is CreatePresentationResult.SdJwt -> JsonPrimitive(presentationResult.serialized)
            is CreatePresentationResult.DeviceResponse -> JsonPrimitive(
                coseCompliantSerializer.encodeToByteArray(presentationResult.deviceResponse)
                    .encodeToString(Base64UrlStrict)
            )
        }
    }
}

sealed interface CreatePresentationResult {
    sealed interface VcJwsPresentationData : CreatePresentationResult

    data class VcJws(
        val serialized: String,
    ) : VcJwsPresentationData

    data class VpJws(
        val serialized: String,
        val jwsSigned: JwsCompactTyped<VerifiablePresentationJws>,
    ) : VcJwsPresentationData

    data class SdJwt(
        val serialized: String,
        val sdJwt: SdJwtSigned,
    ) : CreatePresentationResult

    data class DeviceResponse(
        val deviceResponse: at.asitplus.iso.DeviceResponse,
    ) : CreatePresentationResult
}

@Deprecated("Support for Presentation Exchange been removed from OpenID4VP")
@Serializable
data class PresentationExchangeCredentialDisclosure<Credential : Any>(
    val credential: Credential,
    val disclosedAttributes: Collection<NormalizedJsonPath>,
)

/**
 * Implementations should return true, when the credential attribute may be disclosed to the verifier.
 */
typealias PathAuthorizationValidator = (credential: SubjectCredentialStore.StoreEntry, attributePath: NormalizedJsonPath) -> Boolean

open class PresentationException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}
