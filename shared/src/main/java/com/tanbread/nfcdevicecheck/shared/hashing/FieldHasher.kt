package com.tanbread.nfcdevicecheck.shared.hashing

import java.security.MessageDigest

object FieldHasher {
    private const val ALGORITHM = "SHA-256"

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
