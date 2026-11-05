package com.matrix.messenger.ui.settings

/** Salted password derivation for a UI gate; this does NOT encrypt the local session/database. */
expect object PinHash {
    /** Legacy SHA-256 only, retained to recognize existing installations. */
    fun of(pin: String): String
    fun create(pin: String): String
    fun verify(pin: String, credential: String): Boolean
    fun isLegacy(credential: String): Boolean
}
