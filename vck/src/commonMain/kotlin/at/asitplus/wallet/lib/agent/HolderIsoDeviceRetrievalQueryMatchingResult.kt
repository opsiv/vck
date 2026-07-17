package at.asitplus.wallet.lib.agent

data class HolderIsoDeviceRetrievalQueryMatchingResult<Credential: Any>(
    override val credentials: List<Credential>,
    val queryMatchingResult: IsoDeviceRetrievalQueryMatchingResult
): HolderPresentationRequestMatchingResult<Credential> {
    val inputDescriptorMatches = queryMatchingResult.inputDescriptorMatches.mapValues {
        it.value.mapKeys {
            credentials[it.key.toInt()]
        }
    }

    fun toDefaultSubmission(): Map<String, PresentationExchangeCredentialDisclosure<Credential>> =
        inputDescriptorMatches.mapNotNull { descriptorCredentialMatches ->
            descriptorCredentialMatches.value.entries.firstNotNullOfOrNull { (credential, matching) ->
                PresentationExchangeCredentialDisclosure(
                    credential = credential,
                    disclosedAttributes = matching.values.mapNotNull {
                        it.firstOrNull()?.normalizedJsonPath
                    },
                )
            }?.let {
                descriptorCredentialMatches.key to it
            }
        }.toMap()
}