package com.tanbread.nfcdevicecheck.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTest {
    @Test
    fun protocolVersionIsOne() {
        assertEquals(1, PROTOCOL_VERSION)
    }
}
