package com.matrix.messenger.data.repository

import kotlinx.coroutines.CoroutineDispatcher

/** Entirely in-memory committed file, including an atomic-writer failure injector. */
class TempStore(private val dispatcher: CoroutineDispatcher) {
    var committed: ByteArray? = null
    var failWrites = false
    var failReads = false
    var legacy: SessionStore.Session? = null
    var writes = 0
    var onWrite: (() -> Unit)? = null

    fun open(): SessionPreferencesStore = SessionPreferencesStore(
        path = { "test-only/settings.json" },
        reader = {
            check(!failReads) { "injected read failure" }
            committed?.copyOf()
        },
        atomicWriter = { _, bytes ->
            check(!failWrites) { "injected atomic replacement failure" }
            onWrite?.invoke()
            committed = bytes.copyOf()
            writes++
        },
        legacyReader = { legacy },
        ioDispatcher = dispatcher
    )
}
