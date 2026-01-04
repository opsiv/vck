package at.asitplus.iso.zk.longfellowZk.resultCode

import at.asitplus.KmmResult

interface NativeResultCode {
    val code: Int

    /** The enum constant that represents success */
    val success: NativeResultCode

    /** The enum constant that represents unknown/unexpected values */
    val unknown: NativeResultCode

    val isSuccess: Boolean get() = this == success

    companion object {
        inline fun <reified E> fromInt(value: Int): E
                where E : Enum<E>, E : NativeResultCode {
            val enumValues = enumValues<E>()
            return enumValues.firstOrNull { it.code == value } ?: enumValues.first { it == it.unknown }
        }
    }
}
class NativeResultException(result: NativeResultCode) :
    Exception("Native call failed with code: ${result.code} ($result)") {
    init {
        if (result.isSuccess) {
            throw IllegalStateException("Cannot create NativeResultException for a success result: $result")
        }
    }
}

object NativeResult {
    inline fun <reified E, T> fromResultCode(resultCode: E, value: T): KmmResult<T>
            where E : Enum<E>, E : NativeResultCode =
        if (resultCode.isSuccess) KmmResult(value)
        else KmmResult.failure(NativeResultException(resultCode))

    inline fun <reified E, T> fromResultCode(pair: Pair<E, T>): KmmResult<T>
            where E : Enum<E>, E : NativeResultCode =
        fromResultCode(pair.first, pair.second)

    inline fun <reified E, T> fromInt(code: Int, value: T): KmmResult<T>
            where E : Enum<E>, E : NativeResultCode =
        fromResultCode(NativeResultCode.fromInt<E>(code), value)

    inline fun <reified E, T> fromInt(pair: Pair<Int, T>): KmmResult<T>
            where E : Enum<E>, E : NativeResultCode =
        fromInt<E, T>(pair.first, pair.second)
}

