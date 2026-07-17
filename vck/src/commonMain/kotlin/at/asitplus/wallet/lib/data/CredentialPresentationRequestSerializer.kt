package at.asitplus.wallet.lib.data

import at.asitplus.openid.dcql.DCQLQuery
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Selects the request model from its protocol-defined JSON shape because these wire objects have no shared class
 * discriminator: DCQL has `credentials`, ISO Device Retrieval has `docRequests`, and the legacy fallback is a
 * Presentation Exchange presentation definition.
 */
@Suppress("DEPRECATION")
object CredentialPresentationRequestSerializer :
    JsonContentPolymorphicSerializer<CredentialPresentationRequest>(CredentialPresentationRequest::class) {

    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<CredentialPresentationRequest> {
        val parameters = element.jsonObject
        return when {
            DCQLQuery.SerialNames.CREDENTIALS in parameters -> CredentialPresentationRequest.DCQLRequest.serializer()
            "docRequests" in parameters -> CredentialPresentationRequest.IsoDeviceRetrieval.serializer() // TODO true for CBOR? or an array?
            else -> CredentialPresentationRequest.PresentationExchangeRequest.serializer()
        }
    }
}
