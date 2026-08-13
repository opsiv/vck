package at.asitplus.wallet.lib.cbor

import at.asitplus.iso.ZkSignedList
import at.asitplus.iso.ZkSystemSpec
import at.asitplus.wallet.lib.zk.iso.IsoMdocZkProof
import kotlin.time.Instant


class MultipazLongfellowIsoMdocZkProof(
    override val zkSystemSpec: ZkSystemSpec,
    override val rawProof: ByteArray,
    override val docType: String,
    override val timestamp: Instant,
    override val issuerZkSignedNamespaces: Map<String, ZkSignedList> = emptyMap(),
    override val deviceZkSignedNamespaces: Map<String, ZkSignedList> = emptyMap(),
    override val msoX5Chain: List<ByteArray>? = null,
): IsoMdocZkProof() {
    override suspend fun verify(): Boolean {
        TODO("Not yet implemented")
    }
}
