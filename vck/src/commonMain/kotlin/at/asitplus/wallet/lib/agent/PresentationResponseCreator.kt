@file:Suppress("DEPRECATION")

package at.asitplus.wallet.lib.agent

import at.asitplus.KmmResult
import at.asitplus.catching
import at.asitplus.dif.ConstraintField
import at.asitplus.dif.InputDescriptor
import at.asitplus.dif.PresentationSubmission
import at.asitplus.dif.PresentationSubmissionDescriptor
import at.asitplus.jsonpath.core.NodeList
import at.asitplus.openid.dcql.DCQLCredentialQueryMatchingResult
import at.asitplus.openid.dcql.DCQLQuery
import at.asitplus.wallet.lib.agent.SubjectCredentialStore.StoreEntry
import at.asitplus.wallet.lib.data.CredentialPresentation
import at.asitplus.wallet.lib.data.CredentialPresentationRequest
import at.asitplus.wallet.lib.data.dif.PresentationSubmissionValidator
import at.asitplus.wallet.lib.procedures.iso.DeviceRetrievalProcedure
import com.benasher44.uuid.uuid4

/** Resolves and validates holder submissions before creating their format-specific response artifacts. */
internal object PresentationResponseCreator {

    @Suppress("DEPRECATION")
    suspend fun create(
        holder: Holder,
        verifiablePresentationFactory: VerifiablePresentationFactory,
        request: PresentationRequestParameters,
        credentialPresentation: CredentialPresentation,
    ): KmmResult<PresentationResponseParameters> = catching {
        when (credentialPresentation) {
            is CredentialPresentation.DCQLPresentation ->
                createDcql(holder, verifiablePresentationFactory, request, credentialPresentation)

            is CredentialPresentation.PresentationExchangePresentation ->
                createPresentationExchange(holder, verifiablePresentationFactory, request, credentialPresentation)

            is CredentialPresentation.IsoDeviceRetrievalPresentation ->
                createDeviceResponse(holder, verifiablePresentationFactory, request, credentialPresentation)
        }
    }

    private suspend fun createDcql(
        holder: Holder,
        factory: VerifiablePresentationFactory,
        request: PresentationRequestParameters,
        presentation: CredentialPresentation.DCQLPresentation,
    ): PresentationResponseParameters.DCQLParameters {
        val dcqlQuery = presentation.presentationRequest.dcqlQuery
        val credentialSubmissions = presentation.credentialQuerySubmissions
            ?: holder.matchDCQLQueryAgainstCredentialStoreV2(dcqlQuery).getOrThrow()
                .toDefaultSubmission(dcqlQuery).getOrThrow()

        DCQLQuery.Procedures.checkCredentialSetQueryRequirements(
            credentialSubmissions = credentialSubmissions.keys,
            requestedCredentialSetQueries = dcqlQuery.requestedCredentialSetQueries,
        ).getOrThrow()

        val presentations = credentialSubmissions.mapValues { (queryId, submissions) ->
            val query = dcqlQuery.credentials.first { it.id == queryId }
            if (query.multiple != true && submissions.size != 1) {
                throw IllegalArgumentException(
                    "Credential query ${query.id} does not allow multiple submission, but ${submissions.size} were provided."
                )
            }
            submissions.map {
                val credential = it.credential
                if (credential is StoreEntry.Vc && !query.requireCryptographicHolderBinding) {
                    if (it.matchingResult !is DCQLCredentialQueryMatchingResult.AllClaimsMatchingResult) {
                        throw IllegalArgumentException("Credential type only allows disclosure of all attributes.")
                    }
                    CreatePresentationResult.VcJws(credential.vcSerialized)
                } else {
                    factory.createVerifiablePresentation(
                        request = request,
                        credential = credential,
                        disclosedAttributes = it.matchingResult,
                    ).getOrThrow()
                }
            }
        }

        return PresentationResponseParameters.DCQLParameters(presentations)
    }

    private suspend fun createDeviceResponse(
        holder: Holder,
        factory: VerifiablePresentationFactory,
        request: PresentationRequestParameters,
        presentation: CredentialPresentation.IsoDeviceRetrievalPresentation,
    ): PresentationResponseParameters.DeviceRetrievalParameters {
        val deviceRequest = presentation.presentationRequest.deviceRequest
        val submissions = presentation.submissions
            ?: holder.matchDeviceRetrievalAgainstCredentialStore(deviceRequest).getOrThrow()
                .toDefaultSubmission().getOrThrow()
        val selectedCredentials = DeviceRetrievalProcedure.validateSubmission(deviceRequest, submissions).getOrThrow()
        val result = factory.createVerifiablePresentation(
            request = request,
            credentialAndDisclosedAttributes = selectedCredentials,
        ).getOrThrow()
        return PresentationResponseParameters.DeviceRetrievalParameters(result.deviceResponse)
    }

    @Suppress("DEPRECATION")
    private suspend fun createPresentationExchange(
        holder: Holder,
        factory: VerifiablePresentationFactory,
        request: PresentationRequestParameters,
        presentation: CredentialPresentation.PresentationExchangePresentation,
    ): PresentationResponseParameters.PresentationExchangeParameters {
        val presentationRequest = presentation.presentationRequest
        val presentationDefinition = presentationRequest.presentationDefinition
        val selection = presentation.inputDescriptorSubmissions
            ?: holder.matchInputDescriptorsAgainstCredentialStoreV2(
                inputDescriptors = presentationDefinition.inputDescriptors,
                fallbackFormatHolder = presentationRequest.fallbackFormatHolder,
            ).getOrThrow().toDefaultSubmission()

        presentationRequest.validateSubmission(holder, selection)
            .onFailure { throw PresentationException(it) }
        val submissions = selection.mapValues {
            PresentationExchangeCredentialDisclosure(
                credential = it.value.credential,
                disclosedAttributes = it.value.disclosedAttributes,
            )
        }.toList()

        return if (request.returnOneDeviceResponse) {
            PresentationResponseParameters.PresentationExchangeParameters(
                presentationSubmission = PresentationSubmission.fromMatches(
                    presentationId = presentationDefinition.id,
                    matches = submissions,
                    isSingleIsoMdocPresentation = true,
                ),
                presentationResults = listOf(
                    factory.createVerifiablePresentation(
                        request = request,
                        credentialAndDisclosedAttributes = submissions.associate {
                            it.second.credential as StoreEntry.Iso to it.second.disclosedAttributes
                        },
                    ).getOrThrow()
                ),
            )
        } else {
            PresentationResponseParameters.PresentationExchangeParameters(
                presentationSubmission = PresentationSubmission.fromMatches(
                    presentationId = presentationDefinition.id,
                    matches = submissions,
                ),
                presentationResults = submissions.map { match ->
                    factory.createVerifiablePresentation(
                        request = request,
                        credential = match.second.credential,
                        disclosedAttributes = match.second.disclosedAttributes,
                    ).getOrThrow()
                },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun CredentialPresentationRequest.PresentationExchangeRequest.validateSubmission(
        holder: Holder,
        credentialSubmissions: Map<String, PresentationExchangeCredentialDisclosure<StoreEntry>>,
    ) = catching {
        val validator = PresentationSubmissionValidator.createInstance(presentationDefinition).getOrThrow()
        require(validator.isValidSubmission(credentialSubmissions.keys)) { "Submission requirements are not satisfied" }

        credentialSubmissions.forEach { submission ->
            val inputDescriptor = presentationDefinition.inputDescriptors
                .firstOrNull { it.id == submission.key }
                ?: throw IllegalArgumentException("Invalid input descriptor id: ${submission.key}")
            val constraintFieldMatches = holder.evaluateInputDescriptorAgainstCredential(
                inputDescriptor = inputDescriptor,
                credential = submission.value.credential,
                fallbackFormatHolder = fallbackFormatHolder,
                pathAuthorizationValidator = { true },
            ).getOrThrow()
            val disclosedAttributes = submission.value.disclosedAttributes.map { it.toString() }

            constraintFieldMatches.filter { it.key.optional != true }.forEach { constraintField ->
                val allowedPaths = constraintField.value.map { it.normalizedJsonPath.toString() }
                disclosedAttributes.firstOrNull { allowedPaths.contains(it) }
                    ?: throw IllegalArgumentException(inputDescriptor.errorMessage(constraintField))
            }
        }
    }

    private fun PresentationSubmission.Companion.fromMatches(
        presentationId: String?,
        matches: List<Pair<String, PresentationExchangeCredentialDisclosure<StoreEntry>>>,
        isSingleIsoMdocPresentation: Boolean = false,
    ) = PresentationSubmission(
        id = uuid4().toString(),
        definitionId = presentationId,
        descriptorMap = matches.mapIndexed { index, match ->
            PresentationSubmissionDescriptor.fromMatch(
                inputDescriptorId = match.first,
                credential = match.second.credential,
                index = if (matches.size == 1 || isSingleIsoMdocPresentation) null else index,
            )
        },
    )

    private fun PresentationSubmissionDescriptor.Companion.fromMatch(
        credential: StoreEntry,
        inputDescriptorId: String,
        index: Int?,
    ) = PresentationSubmissionDescriptor(
        id = inputDescriptorId,
        format = credential.claimFormat,
        // OpenID4VP 1.0 section 6.1: use the root for one VP and an indexed path for several VPs.
        path = index?.let { "\$[$it]" } ?: "\$",
    )

    private fun InputDescriptor.errorMessage(field: Map.Entry<ConstraintField, NodeList>): String =
        "Input descriptor constraints are not satisfied: ${details(field)}"

    private fun InputDescriptor.details(field: Map.Entry<ConstraintField, NodeList>): String =
        "${id}.${field.key.id?.let { " Missing field: $it" }}"
}
