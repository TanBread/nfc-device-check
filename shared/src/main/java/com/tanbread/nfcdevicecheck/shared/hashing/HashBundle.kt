package com.tanbread.nfcdevicecheck.shared.hashing

/**
 * Per-field SHA-256 hashes of the sender's system/hardware identity. Raw
 * values never leave the sender device; only this bundle travels over NFC.
 *
 * The field set is dynamic: every field the sender can scrape gets its own
 * hash, and fields are compared independently so the checker can report which
 * specific field mismatched on a deny.
 */
data class HashBundle(val fields: Map<String, String>) {

    fun toJson(): String = fields.entries.joinToString(
        separator = ",",
        prefix = "{",
        postfix = "}",
    ) { (key, value) -> "\"${escape(key)}\":\"$value\"" }

    /** Field-by-field comparison against [other] (null equals absent). */
    fun compare(other: HashBundle): FieldComparison {
        val mismatched = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for ((key, value) in fields) {
            val theirs = other.fields[key]
            when {
                theirs == null -> missing.add(key)
                theirs == value -> Unit
                else -> mismatched.add(key)
            }
        }
        val notEnrolled = other.fields.keys.filterNot { it in fields }
        return FieldComparison(
            mismatched = mismatched,
            missing = missing,
            notEnrolled = notEnrolled,
        )
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private val ENTRY = Regex("\"([^\"\\\\]+)\"\\s*:\\s*\"([^\"\\\\]*)\"")

        fun fromRaw(values: Map<String, String?>): HashBundle = HashBundle(
            values.entries
                .filter { !it.value.isNullOrBlank() }
                .associate { it.key to FieldHasher.sha256Hex(it.value!!.trim()) },
        )

        fun fromJson(json: String): HashBundle? = try {
            val fields = LinkedHashMap<String, String>()
            ENTRY.findAll(json).forEach { fields[it.groupValues[1]] = it.groupValues[2] }
            if (fields.isEmpty()) null else HashBundle(fields)
        } catch (e: Exception) {
            null
        }
    }
}

data class FieldComparison(
    /** Keys present in both bundles with different hashes. */
    val mismatched: List<String>,
    /** Keys enrolled but absent from the received bundle. */
    val missing: List<String>,
    /** Keys received but never enrolled. */
    val notEnrolled: List<String>,
) {
    val allMatch: Boolean
        get() = mismatched.isEmpty() && missing.isEmpty() && notEnrolled.isEmpty()

    fun mismatchedFields(): List<String> = buildList {
        addAll(mismatched)
        missing.forEach { add("$it (missing)") }
        notEnrolled.forEach { add("$it (not enrolled)") }
    }
}
