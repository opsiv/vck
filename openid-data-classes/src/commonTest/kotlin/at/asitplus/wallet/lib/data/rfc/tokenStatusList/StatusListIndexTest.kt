package at.asitplus.wallet.lib.data.rfc.tokenStatusList

import at.asitplus.testballoon.matrix.matrixSuite
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import io.kotest.assertions.throwables.shouldThrow

val StatusListIndexTest by matrixSuite {
    "negative status-list indices are rejected" {
        shouldThrow<IllegalArgumentException> {
            StatusListInfo(-1, UniformResourceIdentifier("https://example.com/status"))
        }
        shouldThrow<IllegalArgumentException> {
            StatusListView(ByteArray(1), TokenStatusBitSize.ONE).getOrNull(-1)
        }
    }
}
