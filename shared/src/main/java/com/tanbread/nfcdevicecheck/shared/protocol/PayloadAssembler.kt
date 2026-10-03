package com.tanbread.nfcdevicecheck.shared.protocol

import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle

/**
 * Reader-side assembly of the sender's payload: issues HEADER/CHUNK/DONE
 * commands over IsoDep and rebuilds the JSON.
 */
class PayloadAssembler {
    private val buffer = java.io.ByteArrayOutputStream()

    var expectedLength: Int = -1
        private set
    var receivedChunks: Int = 0
        private set

    val isComplete: Boolean
        get() = expectedLength >= 0 && buffer.size() == expectedLength

    var totalChunks: Int = 0
        private set

    /** Commands still needed to finish the transfer, in order. */
    fun nextCommand(): ByteArray = when {
        expectedLength < 0 -> ApduProtocol.headerCommand()
        !isComplete -> ApduProtocol.chunkCommand(receivedChunks)
        else -> ApduProtocol.doneCommand()
    }

    /** Feed the HEADER response; returns false if malformed/unsupported version. */
    fun onHeader(data: ByteArray): Boolean {
        val header = ApduProtocol.parseHeader(data) ?: return false
        if (header.version != ApduProtocol.PROTOCOL_VERSION) return false
        expectedLength = header.totalLength
        totalChunks = header.chunkCount
        buffer.reset()
        receivedChunks = 0
        return true
    }

    /** Feed a CHUNK response; returns false if it overruns the declared length. */
    fun onChunk(data: ByteArray): Boolean {
        if (expectedLength < 0) return false
        if (buffer.size() + data.size > expectedLength) return false
        buffer.write(data)
        receivedChunks++
        return true
    }

    fun payload(): ByteArray? = if (isComplete) buffer.toByteArray() else null

    fun bundle(): HashBundle? = payload()?.let {
        HashBundle.fromJson(String(it, Charsets.UTF_8))
    }
}
