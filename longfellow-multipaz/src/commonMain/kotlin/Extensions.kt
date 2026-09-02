package at.asitplus.wallet.lib.cbor

import at.asitplus.iso.ZkDocument
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import kotlinx.serialization.decodeFromByteArray
import org.multipaz.cbor.Cbor
import org.multipaz.mdoc.zkp.ZkSystemParamValue
import org.multipaz.mdoc.zkp.ZkSystemSpec as MultipazZkSystemSpec
import org.multipaz.mdoc.zkp.ZkDocument as MultipazZkDocument

internal fun MultipazZkDocument.toZkDocument(): ZkDocument =
    coseCompliantSerializer.decodeFromByteArray(Cbor.encode(toDataItem()))

internal fun MultipazZkSystemSpec.copyWithParameters(
    id: String = this.id,
    system: String = this.system,
): MultipazZkSystemSpec {
    return this.copy(id = id, system = system).also { newSpec ->
        // manually copy parameters becuase it's not covered by Multipaz's data class copy()
        this.params.forEach { (key, value) ->
            when (value) {
                is ZkSystemParamValue.StringValue -> newSpec.addParam(key, value.value)
                is ZkSystemParamValue.LongValue -> newSpec.addParam(key, value.value)
                is ZkSystemParamValue.DoubleValue -> newSpec.addParam(key, value.value)
                is ZkSystemParamValue.BooleanValue -> newSpec.addParam(key, value.value)
            }
        }
    }
}

