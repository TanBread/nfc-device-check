package com.tanbread.nfcdevicecheck.checker.verify

import com.tanbread.nfcdevicecheck.checker.data.EnrolledDevice
import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifierTest {

    private val enrolled = EnrolledDevice.fromBundle(
        label = "Pixel B",
        bundle = HashBundle.fromRaw(
            mapOf(
                "Model" to "Pixel 8",
                "Build number" to "BP2A.250605.031",
                "Android version" to "17",
                "GPU renderer" to "Adreno (TM) 740",
            ),
        ),
    )

    private fun rawBundle(
        model: String = "Pixel 8",
        build: String = "BP2A.250605.031",
        android: String = "17",
        gpu: String = "Adreno (TM) 740",
    ) = HashBundle.fromRaw(
        mapOf(
            "Model" to model,
            "Build number" to build,
            "Android version" to android,
            "GPU renderer" to gpu,
        ),
    )

    @Test
    fun `exact match accepts`() {
        val outcome = Verifier.verify(rawBundle(), listOf(enrolled))
        assertTrue(outcome is VerifyOutcome.Accept)
        assertEquals("Pixel B", (outcome as VerifyOutcome.Accept).device.label)
    }

    @Test
    fun `changed build number denies with that field reported`() {
        val outcome = Verifier.verify(rawBundle(build = "WRONG"), listOf(enrolled))
        assertTrue(outcome is VerifyOutcome.Deny)
        outcome as VerifyOutcome.Deny
        assertEquals(listOf("Build number"), outcome.comparison.mismatchedFields())
        assertEquals("Pixel B", outcome.closest.label)
    }

    @Test
    fun `no enrolled devices reports that state`() {
        val outcome = Verifier.verify(rawBundle(), emptyList())
        assertEquals(VerifyOutcome.NoEnrolledDevices, outcome)
    }

    @Test
    fun `deny log records mismatched fields and received bundle`() {
        val received = rawBundle(model = "Pixel 9", gpu = "Immortalis-G715")
        val outcome = Verifier.verify(received, listOf(enrolled))
        val log = Verifier.toLog(outcome, received)
        assertEquals(false, log.accepted)
        assertEquals("Pixel B", log.deviceLabel)
        assertTrue(log.mismatchedFields.contains("Model"))
        assertTrue(log.mismatchedFields.contains("GPU renderer"))
        assertEquals(received, HashBundle.fromJson(log.receivedBundleJson))
    }

    @Test
    fun `accept log has no mismatches`() {
        val received = rawBundle()
        val log = Verifier.toLog(Verifier.verify(received, listOf(enrolled)), received)
        assertEquals(true, log.accepted)
        assertEquals("", log.mismatchedFields)
    }
}
