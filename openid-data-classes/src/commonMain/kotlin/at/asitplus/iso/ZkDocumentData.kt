package at.asitplus.iso

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ValueTags
import kotlin.time.Instant

/**
 * Part of the ISO/IEC 18013-5:2026 standard: ZKP Mdoc response (10.3.4)
 */
@Serializable
data class ZkDocumentData (
    @SerialName("docType")
    val docType: String,
    @SerialName("zkSystemId")
    val zkSystemId: String,
    @SerialName("timestamp")
    @ValueTags(0u)
    val timestamp: Instant,
    @SerialName("issuerSigned")
    @Serializable(with = NamespacedZkSignedListSerializer::class)
    val issuerSigned: Map<String, @Contextual ZkSignedList>? = null,
    @SerialName("deviceSigned")
    @Serializable(with = NamespacedZkSignedListSerializer::class)
    val deviceSigned: Map<String, @Contextual ZkSignedList>? = null,
    @SerialName(PROP_ELEMENT_CERT_CHAIN)
    @Serializable(with = NormalizedX509Serializer::class)
    val certificateChain: List<ByteArray>? = null,
) {
    companion object {
        internal const val PROP_ELEMENT_CERT_CHAIN = "msoX5chain"
    }
}
