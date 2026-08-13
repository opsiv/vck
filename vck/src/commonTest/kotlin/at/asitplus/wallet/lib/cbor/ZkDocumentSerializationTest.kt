package at.asitplus.wallet.lib.cbor

import at.asitplus.iso.ZkDocument
import at.asitplus.iso.ZkDocumentData
import at.asitplus.iso.ZkSignedItem
import at.asitplus.iso.ZkSignedList
import at.asitplus.signum.indispensable.cosef.io.ByteStringWrapper
import at.asitplus.signum.indispensable.cosef.io.coseCompliantSerializer
import at.asitplus.testballoon.matrix.matrixSuite
import com.benasher44.uuid.uuid4
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToByteArray
import kotlin.time.Instant

val ZkDocumentSerializationTest by matrixSuite {

    "Serialize then Deserialize to get identity" {
        val doc = ZkDocument(
            ByteStringWrapper(
                ZkDocumentData(
                    docType = uuid4().toString(),
                    zkSystemId = uuid4().toString(),
                    timestamp = Instant.parse("2026-01-01T00:00:00Z"),
                    issuerSigned = mapOf(
                        uuid4().toString() to ZkSignedList(
                            mutableListOf(
                                ZkSignedItem(
                                    uuid4().toString(),
                                    uuid4().toString()
                                ),
                                ZkSignedItem(
                                    uuid4().toString(),
                                    uuid4().toString()
                                ),
                                ZkSignedItem(
                                    uuid4().toString(),
                                    uuid4().toString()
                                )
                            )
                        ),
                        uuid4().toString() to ZkSignedList(
                            mutableListOf(
                                ZkSignedItem(
                                    uuid4().toString(),
                                    uuid4().toString()
                                )
                            )
                        ),

                        ),
                    deviceSigned = mapOf(
                        uuid4().toString() to ZkSignedList(
                            mutableListOf(
                                ZkSignedItem(
                                    uuid4().toString(),
                                    uuid4().toString()
                                ),
                                ZkSignedItem(
                                    uuid4().toString(),
                                    uuid4().toString()
                                )
                            )
                        )
                    ),
                )
            ),
            proof = "test".encodeToByteArray()
        )

        val serialized = coseCompliantSerializer.encodeToByteArray(doc)
        val deserialized = coseCompliantSerializer.decodeFromByteArray(ZkDocument.serializer(), serialized)

        doc shouldBeEqual deserialized
    }

    "Serialize msoX5chain with its textual Multipaz-compatible field name" {
        val doc = ZkDocument(
            ByteStringWrapper(
                ZkDocumentData(
                    docType = "org.example.test",
                    zkSystemId = "example-zk",
                    timestamp = Instant.parse("2026-01-01T00:00:00Z"),
                    certificateChain = listOf(byteArrayOf(1, 2, 3)),
                )
            ),
            proof = byteArrayOf(4, 5, 6),
        )

        val serialized = coseCompliantSerializer.encodeToByteArray(doc)
        val fieldName = "msoX5chain".encodeToByteArray().toList()

        serialized.toList().windowed(fieldName.size).any { it == fieldName } shouldBe true
    }

}
