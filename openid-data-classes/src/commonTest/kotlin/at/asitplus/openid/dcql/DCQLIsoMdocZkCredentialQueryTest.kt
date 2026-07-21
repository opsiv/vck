package at.asitplus.openid.dcql

import at.asitplus.openid.CredentialFormatEnum
import at.asitplus.testballoon.matrix.matrixSuite
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

val DCQLIsoMdocZkCredentialQueryTest by matrixSuite {
    "serialization" {
        val value = DCQLIsoMdocZkCredentialQuery(
            id = DCQLCredentialQueryIdentifier("test"),
            format = CredentialFormatEnum.MSO_MDOC_ZK,
            meta = DCQLIsoMdocZkCredentialMetadataAndValidityConstraints(
                doctypeValue = "test",
                zkSystemType = DCQLIsoMdocZkSystemType(
                    listOf(
                        DCQLIsoMdocZkSystemSpec(
                            zkSystemId = "testId1",
                            system = "testSystem",
                            circuitHash = "testCircuitHash",
                            numAttributes = 1,
                            version = 3,
                            blockEncHash = 3,
                            blockEncSig = 7,
                        ),
                        DCQLIsoMdocZkSystemSpec(
                            zkSystemId = "testId2",
                            system = "testSystem",
                            circuitHash = "testCircuitHash",
                            numAttributes = 3,
                            version = 27
                        )
                    )
                )
            ),
        )

        val base: DCQLCredentialQuery = value
        val serialized = Json.encodeToJsonElement(base)
        serialized shouldBe Json.encodeToJsonElement(value)
        serialized.jsonObject.entries shouldHaveSize 3

        DCQLCredentialQuery.SerialNames.ID shouldBeIn serialized.jsonObject.keys
        DCQLCredentialQuery.SerialNames.FORMAT shouldBeIn serialized.jsonObject.keys
        DCQLCredentialQuery.SerialNames.META shouldBeIn serialized.jsonObject.keys
    }
}
