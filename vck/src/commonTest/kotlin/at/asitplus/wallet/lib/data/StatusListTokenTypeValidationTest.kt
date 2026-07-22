package at.asitplus.wallet.lib.data

import at.asitplus.KmmResult
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.supreme.sign.Verifier
import at.asitplus.testballoon.matrix.matrixSuite
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListInfo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

val StatusListTokenTypeValidationTest by matrixSuite {
    "jwt status list token type validation" - {
        "accepts typ=statuslist+jwt" {
            val issued = StatusListAgent().issueStatusListJwt()
            val statusListToken = StatusListJwt(issued, resolvedAt = null)

            statusListToken.validate(
                verifyJwsObject = { KmmResult.success(Verifier.Success) },
                revocationListInfo = StatusListInfo(index = 0, uri = issued.payload.subject),
                isInstantInThePast = { false },
            ).isSuccess shouldBe true
        }

        "rejects typ=application/statuslist+jwt" {
            val issued = StatusListAgent().issueStatusListJwt()
            val statusListToken = StatusListJwt(
                value = issued.copy(
                    jws = JwsCompact(
                        protectedHeader = issued.jws.jwsHeader.copy(type = MediaTypes.Application.STATUSLIST_JWT),
                        payload = issued.jws.plainPayload,
                        signer = { issued.jws.plainSignature },
                    )
                ),
                resolvedAt = null,
            )

            statusListToken.validate(
                verifyJwsObject = { KmmResult.success(Verifier.Success) },
                revocationListInfo = StatusListInfo(index = 0, uri = issued.payload.subject),
                isInstantInThePast = { false },
            ).exceptionOrNull().toString().shouldContain("Invalid type header")
        }
    }
}
