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

internal fun MultipazZkDocument.toZkDocument(): ZkDocument =
 coseCompliantSerializer.decodeFromByteArray(Cbor.encode(toDataItem()))


