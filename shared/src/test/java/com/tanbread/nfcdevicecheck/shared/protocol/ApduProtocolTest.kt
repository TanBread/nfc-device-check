package com.tanbread.nfcdevicecheck.shared.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApduProtocolTest {

    @Test
    fun `select command parses back to aid`() {
        val select = ApduProtocol.selectCommand()
        assertTrue(ApduProtocol.isSelect(select))
        val data = ApduProtocol.selectData(select)
        assertTrue(ApduProtocol.isAidMatch(data))
    }

    @Test
    fun `header response round trips`() {
        val response = ApduProtocol.headerResponse(500, 3)
        val header = ApduProtocol.parseHeader(response)!!
        assertEquals(ApduProtocol.PROTOCOL_VERSION, header.version)
        assertEquals(500, header.totalLength)
        assertEquals(3, header.chunkCount)
    }

    @Test
    fun `header rejects short data`() {
        assertNull(ApduProtocol.parseHeader(byteArrayOf(1, 2)))
    }

    @Test
    fun `chunk count covers partial chunks`() {
        assertEquals(0, ApduProtocol.chunkCount(0))
        assertEquals(1, ApduProtocol.chunkCount(1))
        assertEquals(1, ApduProtocol.chunkCount(ApduProtocol.CHUNK_SIZE))
        assertEquals(2, ApduProtocol.chunkCount(ApduProtocol.CHUNK_SIZE + 1))
        assertEquals(3, ApduProtocol.chunkCount(ApduProtocol.CHUNK_SIZE * 3))
    }

    @Test
    fun `split produces ordered chunks that rejoin`() {
        val payload = ByteArray(600) { it.toByte() }
        val chunks = ApduProtocol.split(payload)
        assertEquals(3, chunks.size)
        assertEquals(ApduProtocol.CHUNK_SIZE, chunks[0].size)
        assertEquals(ApduProtocol.CHUNK_SIZE, chunks[1].size)
        assertEquals(600 - ApduProtocol.CHUNK_SIZE * 2, chunks[2].size)
        val rejoined = java.io.ByteArrayOutputStream().apply {
            chunks.forEach { write(it) }
        }.toByteArray()
        assertArrayEquals(payload, rejoined)
    }

    @Test
    fun `reader commands parse on card side`() {
        val header = ApduProtocol.parseCommand(ApduProtocol.headerCommand())!!
        assertEquals(ApduProtocol.OP_HEADER, header.op)
        assertEquals(0, header.param)

        val chunk = ApduProtocol.parseCommand(ApduProtocol.chunkCommand(42))!!
        assertEquals(ApduProtocol.OP_CHUNK, chunk.op)
        assertEquals(42, chunk.param)

        val done = ApduProtocol.parseCommand(ApduProtocol.doneCommand())!!
        assertEquals(ApduProtocol.OP_DONE, done.op)
    }

    @Test
    fun `malformed commands rejected`() {
        assertNull(ApduProtocol.parseCommand(byteArrayOf(0x00)))
        assertNull(ApduProtocol.parseCommand(byteArrayOf(0x80.toByte(), 0x01, 0x00, 0x00)))
    }

    @Test
    fun `assembler rebuilds payload from chunk stream`() {
        val json = """{"Model":"aa","Build number":"bb","Android version":"cc"}"""
        val payload = json.toByteArray(Charsets.UTF_8)
        val chunks = ApduProtocol.split(payload)

        val assembler = PayloadAssembler()
        assertFalse(assembler.isComplete)
        assertArrayEquals(ApduProtocol.headerCommand(), assembler.nextCommand())

        assertTrue(assembler.onHeader(ApduProtocol.headerResponse(payload.size, chunks.size)))
        assertEquals(payload.size, assembler.expectedLength)
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, assembler.receivedChunks)
            assertArrayEquals(ApduProtocol.chunkCommand(index), assembler.nextCommand())
            assertTrue(assembler.onChunk(chunk))
        }

        assertTrue(assembler.isComplete)
        assertNotNull(assembler.bundle())
        assertEquals(json, String(assembler.payload()!!, Charsets.UTF_8))
    }

    @Test
    fun `assembler rejects wrong version header`() {
        val assembler = PayloadAssembler()
        val bad = byteArrayOf(0x7F, 0x00, 0x10, 0x00, 0x01)
        assertFalse(assembler.onHeader(bad))
    }

    @Test
    fun `assembler rejects chunk overrun`() {
        val assembler = PayloadAssembler()
        assembler.onHeader(ApduProtocol.headerResponse(4, 1))
        assertFalse(assembler.onChunk(ByteArray(5)))
    }
}
