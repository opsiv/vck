@file:Suppress("PropertyName", "FunctionName", "LocalVariableName")

package at.asitplus.iso.zk.longfellowZk.backend

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference

internal interface IJnaLibrary : Library {
    fun generate_circuit(
        zk_spec_version: Pointer?,
        cb: PointerByReference,
        clen: LongByReference
    ): Int

    fun run_mdoc_prover(
        bcp: ByteArray, bcsz: Long,
        mdoc: ByteArray, mdoc_len: Long,
        pkx: String, pky: String,
        transcript: ByteArray, tr_len: Long,
        attrs: Array<RequestedAttributeStruct>, attrs_len: Long,
        now: String,
        prf: PointerByReference, proof_len: LongByReference,
        zkSpec: Pointer?
    ): Int

    fun run_mdoc_verifier(
        bcp: ByteArray, bcsz: Long,
        pkx: String, pky: String,
        transcript: ByteArray, tr_len: Long,
        attrs: Array<RequestedAttributeStruct>, attrs_len: Long,
        now: String,
        prf: ByteArray, proof_len: Long,
        doc_type: String,
        zkSpec: Pointer?
    ): Int

    fun find_zk_spec(system_name: String, circuit_hash: String): Pointer?
}
