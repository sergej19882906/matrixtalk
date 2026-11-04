package com.matrix.messenger.ui.settings

/** PIN hashing (SHA-256 via platform implementations, no plain storage). */
expect object PinHash {
    fun of(pin: String): String
}
