package at.asitplus.wallet.lib.procedures.iso

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.iso.IssuerSigned
import at.asitplus.iso.ItemsRequest
import at.asitplus.wallet.lib.agent.IsoDeviceRetrievalClaimMatch
import at.asitplus.wallet.lib.agent.PresentationException

/** Evaluates the required data elements of one ISO `ItemsRequest` against one mdoc credential. */
object DeviceRetrievalInputEvaluator {

    /**
     * Returns all requested claims only if the credential contains every requested namespace and data element.
     * `intentToRetain` controls verifier retention and does not make an element optional.
     */
    operator fun invoke(
        itemsRequest: ItemsRequest,
        issuerSigned: IssuerSigned,
    ): KmmResult<List<IsoDeviceRetrievalClaimMatch>> = catching {
        itemsRequest.namespaces.flatMap { (namespace, requestedItems) ->
            requestedItems.entries.map { request ->
                val item = issuerSigned.namespaces?.get(namespace)?.entries
                    ?.firstOrNull { it.value.elementIdentifier == request.dataElementIdentifier }
                    ?.value
                    ?: throw PresentationException(
                        "Credential does not contain requested data element $['$namespace']['${request.dataElementIdentifier}']"
                    )
                IsoDeviceRetrievalClaimMatch(
                    namespace = namespace,
                    claimName = item.elementIdentifier,
                    claimValue = item.elementValue,
                )
            }
        }
    }
}
