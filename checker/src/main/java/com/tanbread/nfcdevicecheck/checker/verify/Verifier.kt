package com.tanbread.nfcdevicecheck.checker.verify

import com.tanbread.nfcdevicecheck.checker.data.CheckLog
import com.tanbread.nfcdevicecheck.checker.data.EnrolledDevice
import com.tanbread.nfcdevicecheck.shared.hashing.FieldComparison
import com.tanbread.nfcdevicecheck.shared.hashing.HashBundle

sealed interface VerifyOutcome {
    /** Matches an enrolled device exactly (all fields). */
    data class Accept(val device: EnrolledDevice) : VerifyOutcome

    /** Closest enrolled device had at least one mismatch. */
    data class Deny(
        val closest: EnrolledDevice,
        val comparison: FieldComparison,
    ) : VerifyOutcome

    /** Nothing enrolled yet — can't decide. */
    data object NoEnrolledDevices : VerifyOutcome
}

/**
 * Compares the received hash bundle against every enrolled device. Accepts on
 * an exact match; otherwise denies and reports the closest device's field
 * breakdown so the user sees exactly what differed.
 */
object Verifier {
    fun verify(received: HashBundle, enrolled: List<EnrolledDevice>): VerifyOutcome {
        if (enrolled.isEmpty()) return VerifyOutcome.NoEnrolledDevices

        enrolled.firstOrNull { it.toBundle().compare(received).allMatch }?.let {
            return VerifyOutcome.Accept(it)
        }

        val closest = enrolled.minByOrNull { fieldDistance(it.toBundle().compare(received)) }
            ?: return VerifyOutcome.NoEnrolledDevices
        return VerifyOutcome.Deny(closest, closest.toBundle().compare(received))
    }

    private fun fieldDistance(comparison: FieldComparison): Int =
        comparison.mismatchedFields().size

    fun toLog(outcome: VerifyOutcome, received: HashBundle): CheckLog = when (outcome) {
        is VerifyOutcome.Accept -> CheckLog(
            accepted = true,
            deviceLabel = outcome.device.label,
            mismatchedFields = "",
            receivedEid = received.eid,
            receivedImei1 = received.imei1,
            receivedImei2 = received.imei2,
            receivedAndroidVersion = received.androidVersion,
            receivedBuildNumber = received.buildNumber,
        )

        is VerifyOutcome.Deny -> CheckLog(
            accepted = false,
            deviceLabel = outcome.closest.label,
            mismatchedFields = outcome.comparison.mismatchedFields().joinToString(", "),
            receivedEid = received.eid,
            receivedImei1 = received.imei1,
            receivedImei2 = received.imei2,
            receivedAndroidVersion = received.androidVersion,
            receivedBuildNumber = received.buildNumber,
        )

        VerifyOutcome.NoEnrolledDevices -> CheckLog(
            accepted = false,
            deviceLabel = null,
            mismatchedFields = "no enrolled devices",
            receivedEid = received.eid,
            receivedImei1 = received.imei1,
            receivedImei2 = received.imei2,
            receivedAndroidVersion = received.androidVersion,
            receivedBuildNumber = received.buildNumber,
        )
    }
}
