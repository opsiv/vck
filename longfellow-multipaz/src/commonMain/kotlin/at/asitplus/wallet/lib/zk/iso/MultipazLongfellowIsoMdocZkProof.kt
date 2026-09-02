package at.asitplus.wallet.lib.zk.iso

import at.asitplus.iso.ZkSignedList
import at.asitplus.iso.ZkSystemSpec
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

//    constructor(zkSystemSpec: ZkSystemSpec,
//                zkDocument: MultipazZkDocument
//    ) : this(
//        zkSystemSpec = zkSystemSpec,
//        rawProof = zkDocument.proof.toByteArray(),
//        docType = zkDocument.documentData.docType,
//        timestamp = zkDocument.documentData.timestamp,
//        issuerZkSignedNamespaces = zkDocument.documentData.
//    )
//
//    MultipazLongfellowIsoMdocZkProof(
//    zkSystemSpec = selectedZkSystemSpec,
//    rawProof = zkDocument.proof,
//    docType = zkDocument.zkDocumentDataBytes.value.docType,
//    timestamp = zkDocument.zkDocumentDataBytes.value.timestamp,
//    issuerZkSignedNamespaces = zkDocument.zkDocumentDataBytes.value.issuerSigned ?: emptyMap(),
//    deviceZkSignedNamespaces = zkDocument.zkDocumentDataBytes.value.deviceSigned ?: emptyMap(),
//    msoX5Chain = zkDocument.zkDocumentDataBytes.value.certificateChain.takeIf { !it.isNullOrEmpty() }
//    ?: credential.issuerSigned.issuerAuth.protectedHeader.certificateChain
//    ?.takeIf { it.isNotEmpty() }
//    ?: credential.issuerSigned.issuerAuth.unprotectedHeader?.certificateChain
//    ?.takeIf { it.isNotEmpty() }

}
