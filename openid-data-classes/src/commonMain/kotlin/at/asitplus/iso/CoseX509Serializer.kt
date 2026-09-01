package at.asitplus.iso

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object CoseX509Serializer : KSerializer<List<ByteArray>> {
    private val listSerializer = ListSerializer(ByteArraySerializer())

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("X509CertificateChainCborSerializer")

    /**
     * Serializes a list of byte arrays representing X.509 certificates into the specified encoder
     * according to [RFC 9360, Section 2](https://www.rfc-editor.org/rfc/rfc9360.html#section-2).
     *
     * Depending on the size of the certificate chain (e.g., the `x5chain` parameter),
     * the CBOR encoding format differs:
     * - **Single Certificate:** Encoded directly as a single CBOR Byte String (`bstr`).
     * - **Multiple Certificates:** Encoded as a CBOR Array of Byte Strings (`[ 2* bstr ]`).
     *
     * @param encoder The encoder to serialize data into.
     * @param value The list of byte arrays (certificates) to encode.
     */
    override fun serialize(encoder: Encoder, value: List<ByteArray>) {
        when {
            value.size == 1 -> encoder.encodeSerializableValue(ByteArraySerializer(), value.first())
            else -> encoder.encodeSerializableValue(listSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): List<ByteArray> {
        // TODO: This deserialization approach is unreliable and will fail in some cases.
        // RFC 9360 defines two distinct CBOR structures (bstr or [+ bstr]) for this field.
        // Because kotlinx.serialization cannot "peek" the type or rewind the stream, if the
        // list decoding fails (e.g., it was actually a single bstr), the decoder's internal
        // state is already advanced/corrupted, causing the fallback to fail as well.
        return runCatching {
            decoder.decodeSerializableValue(listSerializer)
        }.getOrElse {
            listOf(decoder.decodeSerializableValue(ByteArraySerializer()))
        }
    }
}