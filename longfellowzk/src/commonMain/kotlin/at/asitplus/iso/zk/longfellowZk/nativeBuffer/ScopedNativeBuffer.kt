package at.asitplus.iso.zk.longfellowZk.nativeBuffer

/**
 * A scoped wrapper around a malloc'ed native buffer.
 *
 * This class guarantees that the underlying native memory is always freed
 * after use. It is **not thread‑safe** and cannot be constructed directly.
 *
 * Typical usage:
 * ```
 * val (blockReturnValue, buffer) = ScopedNativeBuffer { byteArrayPointer, byteArrayLengthPointer ->
 *     // Call some native function that writes into byteArrayPointer and byteArrayLengthPointer
 *     // The lambda's last expression becomes blockReturnValue
 * }
 * // `blockReturnValue` is whatever the block returned,
 * // `buffer` is a copy of the native memory contents
 * ```
 *
 * The buffer is automatically freed once the block completes, even if an
 * exception is thrown. Callers never see a `ScopedNativeBuffer` instance
 * directly; the only public entry point is [invoke].
 */
class ScopedNativeBuffer private constructor(
) : NativeBuffer by NativeBufferDelegate() {

    private fun <T> run(block: (NativeBufferPointers) -> T): Pair<T, ByteArray> {
        return try {
            block(pointers) to snapshot()
        }
        finally {
            free()
        }
    }

    companion object {
        /**
         * Creates a new scoped buffer, executes [block], and returns the block's
         * result together with a copy of the buffer contents.
         *
         * This is the preferred entry point for external callers: the underlying
         * native buffer is guaranteed to be freed after [block] completes.
         */
        operator fun <T> invoke(
            block: (NativeBufferPointers) -> T
        ): Pair<T, ByteArray> {
            val buffer = ScopedNativeBuffer()
            return buffer.run(block)
        }
    }
}



