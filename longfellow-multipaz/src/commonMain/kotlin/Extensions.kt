package at.asitplus.wallet.lib.cbor

import at.asitplus.iso.ZkDocument
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import kotlinx.serialization.decodeFromByteArray
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.buildCborMap
import org.multipaz.mdoc.zkp.ZkDocument as MultipazZkDocument

/**
 * Converts a Multipaz [ZkDocument][org.multipaz.mdoc.zkp.ZkDocument] into a typed [ZkDocument].
 *
 * **Normalization Workaround:**
 * This function actively rewrites the underlying CBOR payload before handing it off to
 * `kotlinx.serialization`. According to RFC 9360, a single X.509 certificate in `msoX5chain`
 * MUST be encoded as a bare byte string (`bstr`). However, our target `CoseX509Serializer`
 * struggles to dynamically peek the CBOR type. If it attempts to decode a list and fails,
 * the stream state is irreversibly consumed, causing fallback mechanisms to crash.
 *
 * To prevent this, this function eagerly normalizes a bare `bstr` in `msoX5chain` into a
 * single-element `CborArray` (`[+ bstr]`), ensuring safe ingestion by the list serializer.
 *
 * TODO: Remove this CBOR pre-normalization once `CoseX509Serializer` natively supports
 * dynamically branching between `bstr` and `[+ bstr]` decoding paths.
 */
internal fun MultipazZkDocument.toZkDocument(): ZkDocument {
    fun DataItem.normalizeDocData(): DataItem {
        if (this !is Bstr) return this
        val innerMap = (Cbor.decode(value) as? CborMap) ?: return this
        if (innerMap["msoX5chain"] !is Bstr) return this

        val updatedInnerMap = buildCborMap {
            innerMap.items.forEach { (key, value) ->
                put(key, if (key.asTstr == "msoX5chain") CborArray(mutableListOf(value)) else value)
            }
        }
        return Bstr(Cbor.encode(updatedInnerMap))
    }

    val dataItem = toDataItem()
    val multipazDocument = dataItem as? CborMap
        ?: return coseCompliantSerializer.decodeFromByteArray(Cbor.encode(dataItem))

    val normalizedDocument = buildCborMap {
        multipazDocument.items.forEach { (key, value) ->
            put(key, if (key.asTstr == "documentData") value.normalizeDocData() else value)
        }
    }

    return coseCompliantSerializer.decodeFromByteArray(Cbor.encode(normalizedDocument))
}

