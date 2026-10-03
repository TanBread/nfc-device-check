package com.tanbread.nfcdevicecheck.sender.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.tanbread.nfcdevicecheck.shared.protocol.ApduProtocol

/**
 * Emulates a card that serves the current HashBundle over the shared APDU
 * protocol: SELECT AID -> HEADER -> CHUNK*n -> DONE.
 *
 * The payload is snapshotted per session so a mid-tap identity change can't
 * corrupt the transfer.
 */
class SenderCardService : HostApduService() {

    private var sessionPayload: ByteArray = EMPTY
    private var sessionChunks: List<ByteArray> = emptyList()

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_UNKNOWN

        if (ApduProtocol.isSelect(apdu)) {
            if (!ApduProtocol.isAidMatch(ApduProtocol.selectData(apdu))) return SW_NOT_FOUND
            val payload = PayloadProvider.payload ?: EMPTY
            sessionPayload = payload
            sessionChunks = ApduProtocol.split(payload)
            return ApduProtocol.SW_OK_BYTES
        }

        val cmd = ApduProtocol.parseCommand(apdu) ?: return SW_UNKNOWN
        return when (cmd.op) {
            ApduProtocol.OP_HEADER -> withData(
                ApduProtocol.headerResponse(
                    totalLength = sessionPayload.size,
                    chunkCount = sessionChunks.size,
                ),
            )

            ApduProtocol.OP_CHUNK -> {
                val chunk = sessionChunks.getOrNull(cmd.param)
                if (chunk == null) ApduProtocol.SW_BAD_PARAM_BYTES
                else withData(chunk)
            }

            ApduProtocol.OP_DONE -> {
                sessionPayload = EMPTY
                sessionChunks = emptyList()
                ApduProtocol.SW_OK_BYTES
            }

            else -> ApduProtocol.SW_UNKNOWN_OP_BYTES
        }
    }

    override fun onDeactivated(reason: Int) {
        sessionPayload = EMPTY
        sessionChunks = emptyList()
    }

    private fun withData(data: ByteArray): ByteArray =
        data + ApduProtocol.SW_OK_BYTES

    companion object {
        private val EMPTY = ByteArray(0)
        private val SW_OK = ApduProtocol.sw(ApduProtocol.SW_OK)
        private val SW_UNKNOWN = ApduProtocol.sw(0x6D00)
        private val SW_NOT_FOUND = ApduProtocol.sw(0x6A82)
    }
}
