package com.tanbread.nfcdevicecheck.shared.hashing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HashBundleTest {

    private val raw = HashBundle.fromRaw(
        mapOf(
            "Manufacturer" to "Google",
            "Model" to "Pixel 8",
            "Android version" to "17",
            "Build number" to "BP2A.250605.031",
            "GPU renderer" to "Adreno (TM) 740",
        ),
    )

    @Test
    fun `hashes are stable sha256 hex`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            FieldHasher.sha256Hex(""),
        )
        val model = raw.fields.getValue("Model")
        assertEquals(64, model.length)
        assertTrue(model.all { it in "0123456789abcdef" })
    }

    @Test
    fun `same raw values produce identical bundles`() {
        val other = HashBundle.fromRaw(
            mapOf(
                "Manufacturer" to "Google",
                "Model" to "Pixel 8",
                "Android version" to "17",
                "Build number" to "BP2A.250605.031",
                "GPU renderer" to "Adreno (TM) 740",
            ),
        )
        assertTrue(raw.compare(other).allMatch)
    }

    @Test
    fun `changed field fails only that field`() {
        val tampered = HashBundle.fromRaw(
            mapOf(
                "Manufacturer" to "Google",
                "Model" to "Pixel 8 Pro",
                "Android version" to "17",
                "Build number" to "BP2A.250605.031",
                "GPU renderer" to "Adreno (TM) 740",
            ),
        )
        val comparison = raw.compare(tampered)
        assertFalse(comparison.allMatch)
        assertEquals(listOf("Model"), comparison.mismatched)
        assertTrue(comparison.missing.isEmpty())
        assertTrue(comparison.notEnrolled.isEmpty())
        assertEquals(listOf("Model"), comparison.mismatchedFields())
    }

    @Test
    fun `missing and unenrolled fields are reported`() {
        val shorter = HashBundle.fromRaw(mapOf("Model" to "Pixel 8"))
        val comparison = raw.compare(shorter)
        assertEquals(
            listOf("Manufacturer", "Android version", "Build number", "GPU renderer"),
            comparison.missing,
        )
        assertTrue(comparison.mismatched.isEmpty())
        assertEquals("Manufacturer (missing)", comparison.mismatchedFields().first())

        val longer = HashBundle.fromRaw(
            mapOf(
                "Manufacturer" to "Google",
                "Model" to "Pixel 8",
                "Android version" to "17",
                "Build number" to "BP2A.250605.031",
                "GPU renderer" to "Adreno (TM) 740",
                "Cameras" to "0:facing=1",
            ),
        )
        val extra = raw.compare(longer)
        assertEquals(listOf("Cameras"), extra.notEnrolled)
        assertEquals(listOf("Cameras (not enrolled)"), extra.mismatchedFields())
    }

    @Test
    fun `json round trip preserves fields`() {
        val parsed = HashBundle.fromJson(raw.toJson())!!
        assertEquals(raw, parsed)
    }

    @Test
    fun `blank and null raw values are dropped`() {
        val sparse = HashBundle.fromRaw(
            mapOf(
                "Manufacturer" to null,
                "Model" to "Pixel 8",
                "Bootloader" to "",
                "Board" to "   ",
            ),
        )
        assertEquals(setOf("Model"), sparse.fields.keys)
        val parsed = HashBundle.fromJson(sparse.toJson())!!
        assertEquals(sparse, parsed)
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(HashBundle.fromJson("not json"))
        assertNull(HashBundle.fromJson("{}"))
        assertNull(HashBundle.fromJson(""))
    }

    @Test
    fun `different inputs hash differently`() {
        assertNotEquals(
            FieldHasher.sha256Hex("17"),
            FieldHasher.sha256Hex("16"),
        )
    }
}
