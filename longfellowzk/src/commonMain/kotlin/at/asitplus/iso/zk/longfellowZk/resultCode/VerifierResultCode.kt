package at.asitplus.iso.zk.longfellowZk.resultCode

enum class VerifierResultCode(override val code: Int) : NativeResultCode {
    VERIFIER_SUCCESS(0),
    VERIFIER_CIRCUIT_PARSING_FAILURE(1),
    VERIFIER_PROOF_TOO_SMALL(2),
    VERIFIER_HASH_PARSING_FAILURE(3),
    VERIFIER_SIGNATURE_PARSING_FAILURE(4),
    VERIFIER_GENERAL_FAILURE(5),
    VERIFIER_NULL_INPUT(6),
    VERIFIER_INVALID_INPUT(7),
    VERIFIER_ARGUMENTS_TOO_SMALL(8),
    VERIFIER_ATTRIBUTE_NUMBER_MISMATCH(9),
    VERIFIER_INVALID_ZK_SPEC_VERSION(10),
    UNKNOWN(-1);

    override val success: NativeResultCode get() = VERIFIER_SUCCESS
    override val unknown: NativeResultCode get() = UNKNOWN

    companion object {
        public val technicalFailures = setOf<VerifierResultCode>(
            VERIFIER_CIRCUIT_PARSING_FAILURE, // Circuit is regenerated on the host, so it can't be invalid
            VERIFIER_NULL_INPUT, // Null can't be passed via Kotlin abstraction
            UNKNOWN // Indicates some other unexpected technical problem (e.g. segfaults, etc)
        )

        public val invalidInputs: Set<VerifierResultCode> by lazy {
            enumValues<VerifierResultCode>().toSet() -
                    technicalFailures -
                    VERIFIER_SUCCESS
        }

        fun fromInt(value: Int): VerifierResultCode {
            return NativeResultCode.fromInt<VerifierResultCode>(value)
        }
    }
}

