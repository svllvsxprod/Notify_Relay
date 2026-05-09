package com.svllvsx.notifyrelay.util

import java.security.MessageDigest

object HashUtils {
    fun sha256(input: String): String = MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
