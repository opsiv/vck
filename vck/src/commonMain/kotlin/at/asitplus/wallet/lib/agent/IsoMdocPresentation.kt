package at.asitplus.wallet.lib.agent

import at.asitplus.iso.Document
import at.asitplus.iso.ZkDocument

sealed class DocumentResult {
    data class Zk(val document: ZkDocument) : DocumentResult()
    data class Plain(val document: Document) : DocumentResult()
}


