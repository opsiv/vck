package at.asitplus.wallet.lib.iso.zk

import at.asitplus.KmmResult
import at.asitplus.iso.SessionTranscript
import at.asitplus.iso.ZkDocument
import at.asitplus.iso.ZkSystemParamRegistry
import at.asitplus.iso.ZkSystem
import at.asitplus.wallet.lib.agent.PresentationConstraints
import at.asitplus.wallet.lib.agent.PresentationConstraintsAndClaims
import at.asitplus.wallet.lib.agent.PresentationException
import at.asitplus.wallet.lib.agent.PresentationRequestParameters
import at.asitplus.wallet.lib.agent.SubjectCredentialStore



object IsoMdocZkProofRegistry {
    private val factories = LinkedHashSet<IsoMdocZkProofFactory>()

    /**
     * Use [register] in your application to register backends for [IsoMdocZkProofFactory].
     */
    fun register(factory: IsoMdocZkProofFactory): KmmResult<IsoMdocZkProofFactory> {
        if (factories.contains(factory)) {
            return KmmResult.failure(IllegalStateException("Factory already registered!"))
        }

        return factory.initialize().fold(
            onSuccess = {
                ZkSystemParamRegistry.register(factory.systemName, factory.paramSerializers)
                factories.add(factory)
                KmmResult.success(factory)
            },
            onFailure = { KmmResult.failure(it) }
        )
    }


    private fun findFactories(zkSystems: List<ZkSystem>): Map<IsoMdocZkProofFactory, List<ZkSystem>> =
        factories.associateWith { factory -> zkSystems.filter { factory.supports(it) } }
            .filterValues { it.isNotEmpty() }


    private fun selectFactory(zkSystems: List<ZkSystem>): Pair<IsoMdocZkProofFactory, List<ZkSystem>> =
        // TODO: Consider using a different strategy than picking the first factory that supports at least one of the requested systems
        findFactories(zkSystems).entries
            .firstOrNull()
            ?.let { it.key to it.value }
            ?: throw PresentationException("No factory found for any of the requested ZK systems!")


    /**
     * Generates a verifiable [IsoMdocZkProof] (including the zero-knowledge proof)
     */
    suspend fun generate(
        request: PresentationRequestParameters,
        credential: SubjectCredentialStore.StoreEntry.Iso,
        parametersAndClaims: PresentationConstraintsAndClaims,
    ): IsoMdocZkProof {
        val claims = parametersAndClaims.claims
        val parameters = parametersAndClaims.presentationConstraints
        require(parameters is PresentationConstraints.IsoMdocZk) { "Constraints incompatible with Iso mDoc!" }
        val (isoMdocZkProofFactory, zkSystems) = selectFactory(parameters.request.systemSpecs)
        return isoMdocZkProofFactory.generate(request, credential, claims, zkSystems)
    }

    /**
     * Assembles a verifiable [IsoMdocZkProof] from an existing [ZkDocument] and available [ZkSystem]s
     */
    fun load(
        availableZkSystems: List<ZkSystem>,
        zkDocument: ZkDocument,
        sessionTranscript: SessionTranscript
    ): IsoMdocZkProof {
        val zkSystemId = zkDocument.zkDocumentDataBytes.value.zkSystemId
        val selectedZkSystem = availableZkSystems.firstOrNull { it.zkSystemId == zkSystemId }
            ?: throw PresentationException("Presentation contains invalid ZKSystemId: $zkSystemId")

        val factory = requireNotNull(factories.firstOrNull { it.supports(selectedZkSystem) }) {
            "No factory found for ZK system: $zkSystemId"
        }

        return factory.load(zkDocument, sessionTranscript, selectedZkSystem)
    }

}