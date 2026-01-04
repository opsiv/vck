package at.asitplus.iso.zk.longfellowZk.resultCode

enum class CircuitResultCode(override val code: Int) : NativeResultCode {
    CIRCUIT_GENERATION_SUCCESS(0),
    CIRCUIT_GENERATION_NULL_INPUT(1),
    CIRCUIT_GENERATION_ZLIB_FAILURE(2),
    CIRCUIT_GENERATION_GENERAL_FAILURE(3),
    CIRCUIT_GENERATION_INVALID_ZK_SPEC_VERSION(4),
    UNKNOWN(-1);

    override val success: NativeResultCode get() = CIRCUIT_GENERATION_SUCCESS
    override val unknown: NativeResultCode get() = UNKNOWN
}
