package com.tanbread.nfcdevicecheck.shared.hashing

/**
 * Per-field SHA-256 hashes of the sender's identity. Raw values never leave
 * the sender device; only this bundle travels over NFC.
 *
 * Fields are compared independently so the checker can report which specific
 * field mismatched on a deny.
 */
data class HashBundle(
    val eid: String?,
    val imei1: String?,
    val imei2: String?,
    val androidVersion: String?,
    val buildNumber: String?,
) {
    fun toJson(): String = buildString {
        append('{')
        appendString("eid", eid)
        append(',')
        appendString("imei1", imei1)
        append(',')
        appendString("imei2", imei2)
        append(',')
        appendString("androidVersion", androidVersion)
        append(',')
        appendString("buildNumber", buildNumber)
        append('}')
    }

    private fun StringBuilder.appendString(key: String, value: String?) {
        append('"').append(key).append('"').append(':')
        if (value == null) {
            append("null")
        } else {
            append('"').append(value).append('"')
        }
    }

    /** Field-by-field comparison; null equals null (both unavailable). */
    fun compare(other: HashBundle): FieldComparison = FieldComparison(
        eid = eid == other.eid,
        imei1 = imei1 == other.imei1,
        imei2 = imei2 == other.imei2,
        androidVersion = androidVersion == other.androidVersion,
        buildNumber = buildNumber == other.buildNumber,
    )

    companion object {
        fun fromRaw(
            eid: String?,
            imei1: String?,
            imei2: String?,
            androidVersion: String?,
            buildNumber: String?,
        ): HashBundle = HashBundle(
            eid = eid?.let(FieldHasher::sha256Hex),
            imei1 = imei1?.let(FieldHasher::sha256Hex),
            imei2 = imei2?.let(FieldHasher::sha256Hex),
            androidVersion = androidVersion?.let(FieldHasher::sha256Hex),
            buildNumber = buildNumber?.let(FieldHasher::sha256Hex),
        )

        fun fromJson(json: String): HashBundle? = try {
            HashBundle(
                eid = extract(json, "eid"),
                imei1 = extract(json, "imei1"),
                imei2 = extract(json, "imei2"),
                androidVersion = extract(json, "androidVersion"),
                buildNumber = extract(json, "buildNumber"),
            )
        } catch (e: Exception) {
            null
        }

        /** Extracts a string-or-null value for a flat key; throws if key missing. */
        private fun extract(json: String, key: String): String? {
            val marker = "\"$key\":"
            val start = json.indexOf(marker)
            if (start < 0) throw IllegalArgumentException("missing key $key")
            val valueStart = start + marker.length
            if (json.startsWith("null", valueStart)) return null
            if (json[valueStart] != '"') throw IllegalArgumentException("bad value for $key")
            val valueEnd = json.indexOf('"', valueStart + 1)
            if (valueEnd < 0) throw IllegalArgumentException("unterminated value for $key")
            return json.substring(valueStart + 1, valueEnd)
        }
    }
}

data class FieldComparison(
    val eid: Boolean,
    val imei1: Boolean,
    val imei2: Boolean,
    val androidVersion: Boolean,
    val buildNumber: Boolean,
) {
    val allMatch: Boolean
        get() = eid && imei1 && imei2 && androidVersion && buildNumber

    fun mismatchedFields(): List<String> = buildList {
        if (!eid) add("EID")
        if (!imei1) add("IMEI 1")
        if (!imei2) add("IMEI 2")
        if (!androidVersion) add("Android version")
        if (!buildNumber) add("Build number")
    }
}
