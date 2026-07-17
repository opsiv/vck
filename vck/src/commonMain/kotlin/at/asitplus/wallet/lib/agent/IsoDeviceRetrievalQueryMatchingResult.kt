package at.asitplus.wallet.lib.agent

import at.asitplus.KmmResult

data class IsoDeviceRetrievalQueryMatchingResult(
    val inputDescriptorMatchingResults: Map<String, List<KmmResult<InputDescriptorMatching>>>
) {
    val inputDescriptorMatches = inputDescriptorMatchingResults.mapValues {
        it.value.mapIndexedNotNull { index, matchingResult ->
            matchingResult.getOrNull()?.let {
                index.toUInt() to it
            }
        }.toMap()
    }
}