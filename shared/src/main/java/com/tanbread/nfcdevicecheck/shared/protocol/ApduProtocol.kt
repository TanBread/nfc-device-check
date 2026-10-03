package com.tanbread.nfcdevicecheck.shared.protocol

/**
 * APDU-level protocol shared by Sender (HCE service) and Checker (reader).
 *
 * Session flow after the reader selects our AID:
 *   1. OP_HEADER  -> [version:1][totalLen:2 BE][chunkCount:2 BE]
 *   2. OP_CHUNK n -> chunk n of the JSON payload (CHUNK_SIZE bytes each)
 *   3. OP_DONE    -> session closed (9000)
 */
object ApduProtocol {
    const val PROTOCOL_VERSION: Int = 1

    /** 8-byte AID: F0 "NFCKEY" 01. */
    val AID: ByteArray = byteArrayOf(
        0xF0.toByte(), 0x4E, 0x46, 0x43, 0x4B, 0x45, 0x59, 0x01,
    )

    /** Max data bytes per response APDU; leaves room for Le within 256. */
    const val CHUNK_SIZE: Int = 240

    const val CLA: Int = 0x00
    const val OP_HEADER: Int = 0x01
    const val OP_CHUNK: Int = 0x02
    const val OP_DONE: Int = 0x03

    const val SW_OK: Int = 0x9000
    const val SW_UNKNOWN_OP: Int = 0x6A82
    const val SW_BAD_PARAM: Int = 0x6B00

    val SW_OK_BYTES: ByteArray = sw(SW_OK)
    val SW_UNKNOWN_OP_BYTES: ByteArray = sw(SW_UNKNOWN_OP)
    val SW_BAD_PARAM_BYTES: ByteArray = sw(SW_BAD_PARAM)

    fun sw(code: Int): ByteArray = byteArrayOf(
        ((code shr 8) and 0xFF).toByte(),
        (code and 0xFF).toByte(),
    )

    fun isSelect(apdu: ByteArray): Boolean = apdu.size >= 5 &&
        apdu[0].toInt() == CLA &&
        (apdu[1].toInt() and 0xFF) == 0xA4 &&
        (apdu[2].toInt() and 0xFF) == 0x04 &&
        apdu[3].toInt() == 0x00

    fun selectData(apdu: ByteArray): ByteArray {
        val lc = apdu[4].toInt() and 0xFF
        return apdu.copyOfRange(5, 5 + lc)
    }

    data class Command(val op: Int, val param: Int)

    /** Parses a non-SELECT command APDU: [CLA][INS][P1][P2]. Returns null on malformed. */
    fun parseCommand(apdu: ByteArray): Command? {
        if (apdu.size < 4) return null
        if (apdu[0].toInt() != CLA) return null
        val op = apdu[1].toInt() and 0xFF
        val param = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        return Command(op, param)
    }

    fun headerResponse(totalLength: Int, chunkCount: Int): ByteArray {
        require(totalLength in 0..0xFFFF) { "payload too large: $totalLength" }
        return byteArrayOf(
            PROTOCOL_VERSION.toByte(),
            ((totalLength shr 8) and 0xFF).toByte(),
            (totalLength and 0xFF).toByte(),
            ((chunkCount shr 8) and 0xFF).toByte(),
            (chunkCount and 0xFF).toByte(),
        )
    }

    data class Header(val version: Int, val totalLength: Int, val chunkCount: Int)

    fun parseHeader(data: ByteArray): Header? {
        if (data.size < 5) return null
        return Header(
            version = data[0].toInt() and 0xFF,
            totalLength = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF),
            chunkCount = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF),
        )
    }

    fun chunkCount(totalLength: Int): Int =
        if (totalLength == 0) 0 else (totalLength + CHUNK_SIZE - 1) / CHUNK_SIZE

    /** Splits [payload] into CHUNK_SIZE slices (last slice may be shorter). */
    fun split(payload: ByteArray): List<ByteArray> =
        payload.asList().chunked(CHUNK_SIZE).map { it.toByteArray() }

    fun isAidMatch(candidate: ByteArray): Boolean = candidate.contentEquals(AID)

    /** SELECT-by-AID command issued by the reader to start a session. */
    fun selectCommand(): ByteArray {
        val cmd = ByteArray(5 + AID.size)
        cmd[0] = CLA.toByte()
        cmd[1] = 0xA4.toByte()
        cmd[2] = 0x04.toByte()
        cmd[3] = 0x00.toByte()
        cmd[4] = AID.size.toByte()
        AID.copyInto(cmd, 5)
        return cmd
    }

    /** Generic reader command: [CLA][INS][P1][P2] with P1P2 = param (BE16). */
    fun command(op: Int, param: Int = 0): ByteArray = byteArrayOf(
        CLA.toByte(),
        op.toByte(),
        ((param shr 8) and 0xFF).toByte(),
        (param and 0xFF).toByte(),
    )

    fun headerCommand(): ByteArray = command(OP_HEADER)

    fun chunkCommand(index: Int): ByteArray = command(OP_CHUNK, index)

    fun doneCommand(): ByteArray = command(OP_DONE)
}
