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
            eid = "89049032000000000001",
            imei1 = "359187091234561",
            imei2 = "359187091234562",
            androidVersion = "17",
            buildNumber = "BP2A.250605.031",
        ),
    )

    private fun rawBundle(
        eid: String = "89049032000000000001",
        imei1: String = "359187091234561",
        imei2: String = "359187091234562",
        androidVersion: String = "17",
        buildNumber: String = "BP2A.250605.031",
    ) = HashBundle.fromRaw(eid, imei1, imei2, androidVersion, buildNumber)

    @Test
    fun `exact match accepts`() {
        val outcome = Verifier.verify(rawBundle(), listOf(enrolled))
        assertTrue(outcome is VerifyOutcome.Accept)
        assertEquals("Pixel B", (outcome as VerifyOutcome.Accept).device.label)
    }

    @Test
    fun `changed imei denies with that field reported`() {
        val outcome = Verifier.verify(rawBundle(imei2 = "999999999999999"), listOf(enrolled))
        assertTrue(outcome is VerifyOutcome.Deny)
        outcome as VerifyOutcome.Deny
        assertEquals(listOf("IMEI 2"), outcome.comparison.mismatchedFields())
        assertEquals("Pixel B", outcome.closest.label)
    }

    @Test
    fun `no enrolled devices reports that state`() {
        val outcome = Verifier.verify(rawBundle(), emptyList())
        assertEquals(VerifyOutcome.NoEnrolledDevices, outcome)
    }

    @Test
    fun `deny log records mismatched fields`() {
        val received = rawBundle(imei1 = "000000000000000", buildNumber = "WRONG")
        val outcome = Verifier.verify(received, listOf(enrolled))
        val log = Verifier.toLog(outcome, received)
        assertEquals(false, log.accepted)
        assertEquals("Pixel B", log.deviceLabel)
        assertTrue(log.mismatchedFields.contains("IMEI 1"))
        assertTrue(log.mismatchedFields.contains("Build number"))
    }

    @Test
    fun `accept log has no mismatches`() {
        val received = rawBundle()
        val log = Verifier.toLog(Verifier.verify(received, listOf(enrolled)), received)
        assertEquals(true, log.accepted)
        assertEquals("", log.mismatchedFields)
    }
}
