package com.matrix.messenger.ui.settings

import java.security.MessageDigest

actual object PinHash {
    actual fun of(pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("matrixtalk:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
