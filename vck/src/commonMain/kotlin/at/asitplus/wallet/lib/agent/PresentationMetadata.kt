package at.asitplus.wallet.lib.agent

import at.asitplus.iso.ZkInfo

sealed interface PresentationMetadata {
    fun isCompatibleWith(credential: SubjectCredentialStore.StoreEntry): Boolean

    data class IsoMdocZk(val request: ZkInfo) : PresentationMetadata {
        override fun isCompatibleWith(credential: SubjectCredentialStore.StoreEntry) = credential is SubjectCredentialStore.StoreEntry.Iso
    }
}