package com.tanbread.nfcdevicecheck.shared.hashing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HashBundleTest {

    private val raw = HashBundle.fromRaw(
        eid = "89049032000000000001",
        imei1 = "359187091234561",
        imei2 = "359187091234562",
        androidVersion = "17",
        buildNumber = "BP2A.250605.031",
    )

    @Test
    fun `hashes are stable sha256 hex`() {
        // Well-known SHA-256 of empty input, as a correctness anchor.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            FieldHasher.sha256Hex(""),
        )
        assertEquals(64, raw.androidVersion!!.length)
        assertTrue(raw.androidVersion!!.all { it in "0123456789abcdef" })
    }

    @Test
    fun `same raw values produce identical bundles`() {
        val other = HashBundle.fromRaw(
            eid = "89049032000000000001",
            imei1 = "359187091234561",
            imei2 = "359187091234562",
            androidVersion = "17",
            buildNumber = "BP2A.250605.031",
        )
        assertTrue(raw.compare(other).allMatch)
    }

    @Test
    fun `changed imei2 fails only imei2`() {
        val tampered = HashBundle.fromRaw(
            eid = "89049032000000000001",
            imei1 = "359187091234561",
            imei2 = "999999999999999",
            androidVersion = "17",
            buildNumber = "BP2A.250605.031",
        )
        val comparison = raw.compare(tampered)
        assertFalse(comparison.allMatch)
        assertTrue(comparison.eid)
        assertTrue(comparison.imei1)
        assertFalse(comparison.imei2)
        assertTrue(comparison.androidVersion)
        assertTrue(comparison.buildNumber)
        assertEquals(listOf("IMEI 2"), comparison.mismatchedFields())
    }

    @Test
    fun `json round trip preserves values`() {
        val json = raw.toJson()
        val parsed = HashBundle.fromJson(json)!!
        assertEquals(raw, parsed)
    }

    @Test
    fun `null fields survive json round trip`() {
        val sparse = HashBundle.fromRaw(
            eid = null,
            imei1 = "359187091234561",
            imei2 = null,
            androidVersion = "17",
            buildNumber = null,
        )
        val parsed = HashBundle.fromJson(sparse.toJson())!!
        assertNull(parsed.eid)
        assertEquals(sparse.imei1, parsed.imei1)
        assertNull(parsed.imei2)
        assertEquals(sparse.androidVersion, parsed.androidVersion)
        assertNull(parsed.buildNumber)
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(HashBundle.fromJson("not json"))
        assertNull(HashBundle.fromJson("{}"))
    }

    @Test
    fun `different inputs hash differently`() {
        assertNotEquals(
            FieldHasher.sha256Hex("17"),
            FieldHasher.sha256Hex("16"),
        )
    }
}
