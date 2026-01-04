package at.asitplus.iso.zk.longfellowZk.nativeBuffer


/**
 * Represents a platform‑specific pointers to a Byte Array. It consists of:
 *   - a pointer to the Byte Array values (`uint8_t**` in C).
 *   - a pointer to the length of the ByteArray (`size_t*` in C)
 *
 * This type is declared as `expect` in common code and resolved to the appropriate
 * platform type in each target, i.e.
 *   - `PointerByReference` on JVM, `CPointerVar<UByteVar>` with citnerop for the Byte Array values
 *   - `LongByReference` on JVM, `size_tVar` with cinterop for the Byte Array length
 *
 * It is primarily used in scenarios where a native function allocates or
 * returns a buffer of bytes and provides the pointer via an out‑parameter.
 */
expect class NativeBufferPointers
