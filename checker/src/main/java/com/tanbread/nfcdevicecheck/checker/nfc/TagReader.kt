package com.tanbread.nfcdevicecheck.checker.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle
import com.tanbread.nfcdevicecheck.shared.protocol.ApduProtocol
import com.tanbread.nfcdevicecheck.shared.protocol.PayloadAssembler

/**
 * Reader-mode driver: waits for a tag (the Sender's HCE card), runs the
 * HEADER/CHUNK/DONE exchange, and delivers the parsed HashBundle.
 *
 * The read is read-only: nothing stored on this device is touched.
 */
class TagReader(
    private val onResult: (Result<HashBundle>) -> Unit,
) : NfcAdapter.ReaderCallback {

    override fun onTagDiscovered(tag: Tag) {
        val result = readTag(tag)
        onResult(result)
    }

    private fun readTag(tag: Tag): Result<HashBundle> = runCatching {
        val iso = IsoDep.get(tag)
            ?: throw IllegalStateException("Tag does not support ISO-DEP")

        iso.use { connection ->
            connection.connect()
            connection.timeout = 5000

            val selectResponse = connection.transceive(ApduProtocol.selectCommand())
            requireSw(selectResponse, ApduProtocol.SW_OK, "SELECT")

            val assembler = PayloadAssembler()

            // HEADER
            val headerResponse = connection.transceive(assembler.nextCommand())
            requireSw(headerResponse, ApduProtocol.SW_OK, "HEADER")
            require(assembler.onHeader(dataOf(headerResponse))) { "bad/unsupported header" }

            // CHUNKs until complete
            while (!assembler.isComplete) {
                val chunkResponse = connection.transceive(assembler.nextCommand())
                requireSw(chunkResponse, ApduProtocol.SW_OK, "CHUNK")
                require(assembler.onChunk(dataOf(chunkResponse))) { "bad chunk" }
            }

            // DONE
            val doneResponse = connection.transceive(ApduProtocol.doneCommand())
            requireSw(doneResponse, ApduProtocol.SW_OK, "DONE")

            assembler.bundle() ?: throw IllegalStateException("payload did not parse")
        }
    }

    private fun dataOf(response: ByteArray): ByteArray =
        if (response.size >= 2) response.copyOfRange(0, response.size - 2) else ByteArray(0)

    private fun requireSw(response: ByteArray, expected: Int, step: String) {
        if (response.size < 2) throw IllegalStateException("$step: empty response")
        val sw = ((response[response.size - 2].toInt() and 0xFF) shl 8) or
            (response[response.size - 1].toInt() and 0xFF)
        if (sw != expected) {
            throw IllegalStateException("$step: unexpected SW %04X".format(sw))
        }
    }
}
