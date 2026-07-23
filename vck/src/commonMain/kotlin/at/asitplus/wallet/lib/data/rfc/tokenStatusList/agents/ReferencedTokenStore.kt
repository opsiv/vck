package at.asitplus.wallet.lib.data.rfc.tokenStatusList.agents

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.Identifier
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.iso18013.IdentifierInfo
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus

/**
 * Stores all tokens that may be referenced to by a [at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListView]
 */
interface ReferencedTokenStore {

    data class StoredCredentialReference(
        val id: String,
        val timePeriod: Int,
        val statusListIndex: Long,
    ) {
        init {
            require(statusListIndex >= 0) { "statusListIndex must be non-negative" }
        }
    }

    /**
     * Called by an `StatusListIssuer` when creating a token (that is a verifiable credential for us)
     * to get a `statusListIndex` and `identifier`.
     */
    suspend fun storeReferencedToken(
        credential: CredentialToBeIssued,
        timePeriod: Int,
    ): KmmResult<StoredCredentialReference>

    /**
     * Returns a list of the status of tokens, represented by their `statusListIndex` for that [timePeriod].
     */
    fun getStatusListView(timePeriod: Int): StatusListView

    /**
     * Returns a list of the status of tokens, represented by their `identifier` for that [timePeriod].
     * All elements in the list are revoked. All others are assumed [TokenStatus.Valid]
     */
    fun getRawIdentifierList(timePeriod: Int): Map<Identifier, IdentifierInfo>

    /**
     * Set the [status] of the referenced token with this [index] for the [timePeriod], if it exists.
     */
    fun setStatus(timePeriod: Int, index: Long, status: TokenStatus): Boolean

    /**
     * Set the status of the referenced token with this [identifier] for the [timePeriod] to revoked, if it exists.
     * Other operations are not supported in the spec.
     */
    fun revokeIdentifier(timePeriod: Int, identifier: ByteArray): Boolean
}
