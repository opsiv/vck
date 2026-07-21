package at.asitplus.wallet.lib.agent

import at.asitplus.iso.ZkInfo

sealed interface PresentationConstraints {
    fun isCompatibleWith(credential: SubjectCredentialStore.StoreEntry): Boolean

    data class IsoMdocZk(val request: ZkInfo) : PresentationConstraints {
        override fun isCompatibleWith(credential: SubjectCredentialStore.StoreEntry) = credential is SubjectCredentialStore.StoreEntry.Iso
    }
    data object None : PresentationConstraints {
        override fun isCompatibleWith(credential: SubjectCredentialStore.StoreEntry) = true
    }
}