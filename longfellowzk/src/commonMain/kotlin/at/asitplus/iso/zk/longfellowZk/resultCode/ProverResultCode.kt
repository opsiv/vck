package at.asitplus.iso.zk.longfellowZk.resultCode

enum class ProverResultCode(override val code: Int) : NativeResultCode {
    PROVER_SUCCESS(0),
    PROVER_NULL_INPUT(1),
    PROVER_INVALID_INPUT(2),
    PROVER_CIRCUIT_PARSING_FAILURE(3),
    PROVER_HASH_PARSING_FAILURE(4),
    PROVER_WITNESS_CREATION_FAILURE(5),
    PROVER_GENERAL_FAILURE(6),
    PROVER_MEMORY_ALLOCATION_FAILURE(7),
    PROVER_INVALID_ZK_SPEC_VERSION(8),
    UNKNOWN(-1);

    override val success: NativeResultCode get() = PROVER_SUCCESS
    override val unknown: NativeResultCode get() = UNKNOWN
}