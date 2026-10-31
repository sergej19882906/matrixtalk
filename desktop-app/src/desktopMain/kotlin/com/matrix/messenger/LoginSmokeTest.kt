package com.matrix.messenger

import com.matrix.messenger.data.repository.SessionStore
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.di.sharedModule
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Smoke test: run against the PACKAGED classpath (ProGuard jars, optionally a
 * runtime without jdk.unsupported via --limit-modules) to reproduce the
 * installer environment where DataStore's sun.misc.Unsafe dependency was missing.
 */
fun main(): Unit = runBlocking {
    // 1. Prefs store must work (write path) on runtimes WITHOUT sun.misc.Unsafe
    SessionStore.clear()
    SessionStore.save(SessionStore.Session("https://smoke.test", "@smoke:test", "DEV", "TOK"))
    val stored = SessionStore.read()
    check(stored?.accessToken == "TOK") { "SessionStore roundtrip failed: $stored" }
    SessionStore.saveThemeMode("dark")
    check(SessionStore.readThemeMode() == "dark") { "Theme mode roundtrip failed" }
    SessionStore.clear()
    println("STORE: OK (no Unsafe required)")

    startKoin { modules(sharedModule) }
    val repo = getKoin().get<MatrixRepository>()
    println("=== smoke test: login with fake credentials on matrix.org ===")
    val result = repo.login("matrix.org", "matrixtalk_smoke_nonexistent", "fakepassword123")
    println("=== RESULT: $result ===")
    if (result.toString().contains("Main dispatcher", ignoreCase = true)) {
        println("!!! DISPATCHER ERROR STILL PRESENT !!!")
        kotlin.system.exitProcess(1)
    }
    if (result.toString().contains("M_FORBIDDEN") || result.toString().contains("Invalid")
        || result.toString().contains("invalid", ignoreCase = true)
    ) {
        println("OK: reached the server, got auth rejection as expected")
        kotlin.system.exitProcess(0)
    }
    println("UNKNOWN RESULT - inspect output above")
    kotlin.system.exitProcess(2)
}
