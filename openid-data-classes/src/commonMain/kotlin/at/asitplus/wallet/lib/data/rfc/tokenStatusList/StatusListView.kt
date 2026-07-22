package at.asitplus.wallet.lib.data.rfc.tokenStatusList

import at.asitplus.signum.indispensable.io.Base64Strict
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatus
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Utility class to access token statuses by index from an uncompressed byte array.
 * In theory this should improve storage size compared to a list of token statuses since ByteArray
 * is stored more efficiently.
 */
data class StatusListView(
    val uncompressed: ByteArray,
    val statusBitSize: TokenStatusBitSize,
) {
    fun isEmpty() = uncompressed.isEmpty()
    fun isNotEmpty() = !isEmpty()

    operator fun get(index: Long) = getOrNull(index)
        ?: throw IndexOutOfBoundsException("Index $index is out of bounds, size ${uncompressed.size.toLong() * Byte.SIZE_BITS / statusBitSize.value.toLong()}")

    @Deprecated("Use a Long index", ReplaceWith("get(index.toLong())"))
    operator fun get(index: UInt) = get(index.toLong())

    @Deprecated("Use a Long index", ReplaceWith("get(index.toLong())"))
    operator fun get(index: ULong) = get(index.toLong().also {
        require(index <= Long.MAX_VALUE.toULong()) { "index must be at most Long.MAX_VALUE" }
    })

    fun getOrNull(index: Long): TokenStatus? {
        require(index >= 0) { "index must be non-negative" }
        val tokenStatusesPerByte = Byte.SIZE_BITS.toLong() / statusBitSize.value.toLong()

        val byteIndex = (index / tokenStatusesPerByte).also {
            if (it > Int.MAX_VALUE) {
                throw IllegalArgumentException("Argument `index` is too big, it must be at most `Int.MAX_VALUE * bits`.")
            }
        }.toInt()
        val byte = uncompressed.getOrNull(byteIndex) ?: return null

        val statusMask = when (statusBitSize) {
            TokenStatusBitSize.ONE -> 0b1u
            TokenStatusBitSize.TWO -> 0b11u
            TokenStatusBitSize.FOUR -> 0xfu
            TokenStatusBitSize.EIGHT -> 0xffu
        }

        val lowestBitOffset = ((index % tokenStatusesPerByte) * statusBitSize.value.toLong()).toInt()
        val mask = statusMask.shl(lowestBitOffset).toByte()

        val tokenStatusByte = byte.and(mask).toUByte().toInt().shr(lowestBitOffset).toUByte()

        return TokenStatus(tokenStatusByte)
    }

    @Deprecated("Use a Long index", ReplaceWith("getOrNull(index.toLong())"))
    fun getOrNull(index: ULong) = getOrNull(index.toLong().also {
        require(index <= Long.MAX_VALUE.toULong()) { "index must be at most Long.MAX_VALUE" }
    })

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as StatusListView

        if (!uncompressed.contentEquals(other.uncompressed)) return false
        if (statusBitSize != other.statusBitSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uncompressed.contentHashCode()
        result = 31 * result + statusBitSize.hashCode()
        return result
    }

    override fun toString(): String {
        return "StatusListView(" +
                "uncompressed=${uncompressed.encodeToString(Base64Strict)}, " +
                "statusBitSize=$statusBitSize" +
                ")"
    }


    companion object {
        /**
         * @param statusBitSize: If set to null, the smallest possible bitsize is chosen
         */
        fun fromTokenStatuses(
            tokenStatuses: List<TokenStatus>,
            statusBitSize: TokenStatusBitSize?,
        ): StatusListView {
            val highestBitByte = tokenStatuses.maxOfOrNull {
                it.value
            } ?: return StatusListView(
                uncompressed = ByteArray(0),
                statusBitSize = statusBitSize ?: TokenStatusBitSize.ONE,
            )

            val requiredBitSize = when {
                highestBitByte > 0b1111u -> TokenStatusBitSize.EIGHT
                highestBitByte > 0b11u -> TokenStatusBitSize.FOUR
                highestBitByte > 0b1u -> TokenStatusBitSize.TWO
                else -> TokenStatusBitSize.ONE
            }

            val usedBitSize = statusBitSize ?: requiredBitSize
            if (usedBitSize.value < requiredBitSize.value) {
                throw IllegalArgumentException("Argument `tokenStatuses` contains entries that do not fit into the size specified in `statusBitSize`.")
            }

            val statusesPerByte = Byte.SIZE_BITS.toUInt() / usedBitSize.value
            val uncompressed = tokenStatuses.chunked(statusesPerByte.toInt()) { chunk ->
                chunk.map {
                    it.value.toByte()
                }.reduceIndexed { index, acc, byte ->
                    val shiftDistance = index.toUInt() * (Byte.Companion.SIZE_BITS.toUInt() / statusesPerByte)
                    val shifted = byte.toInt().shl(shiftDistance.toInt()).toByte()
                    acc.or(shifted)
                }
            }.toByteArray()

            return StatusListView(
                statusBitSize = usedBitSize,
                uncompressed = uncompressed,
            )
        }
    }
}
