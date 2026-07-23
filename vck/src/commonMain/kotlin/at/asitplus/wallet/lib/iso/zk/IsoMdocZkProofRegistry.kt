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
        if (!factories.contains(factory)) {
            val initResult = factory.initialize()
            return initResult.fold(
                onSuccess = {
                    ZkSystemParamRegistry.register(factory.systemName, factory.paramSerializers)
                    factories.add(factory)
                    KmmResult.success(factory)
                },
                onFailure = { KmmResult.failure(it) }
            )
        } else {
            return KmmResult.failure(IllegalStateException("Factory already registered!"))
        }
    }


    private fun findFactories(zkSystems: List<ZkSystem>): List<Pair<IsoMdocZkProofFactory, ZkSystem>> {
        val matches = zkSystems.flatMap { system ->
            factories
                .filter { factory -> factory.supports(system) }
                .map { factory -> factory to system }
        }

        if (matches.isEmpty()) {
            throw PresentationException("Unsupported zkSystem!")
        }

        return matches
    }

    // TODO: Consider employing sorting for multiple hits
    private fun findFactory(zkSystems: List<ZkSystem>): Pair<IsoMdocZkProofFactory, ZkSystem> {
        val matches = findFactories(zkSystems)
        return matches.first()
    }

    suspend fun generate(
        request: PresentationRequestParameters,
        credential: SubjectCredentialStore.StoreEntry.Iso,
        parametersAndClaims: PresentationConstraintsAndClaims,
    ): IsoMdocZkProof {
        val claims = parametersAndClaims.claims
        val parameters = parametersAndClaims.presentationConstraints
        require(parameters is PresentationConstraints.IsoMdocZk) { "Constraints incompatible with Iso mDoc!" }
        val (isoMdocZkProofFactory, zkSystemSpec) = findFactory(parameters.request.systemSpecs)
        return isoMdocZkProofFactory.generate(request, credential, claims, zkSystemSpec)
    }

    fun load(
        zkSystems: List<ZkSystem>,
        zkDocument: ZkDocument,
        sessionTranscript: SessionTranscript
    ): IsoMdocZkProof {
        val (isoMdocZkProofFactory, zkSystem) = findFactory(zkSystems)
        return isoMdocZkProofFactory.load(zkDocument, sessionTranscript, zkSystem)
    }

}