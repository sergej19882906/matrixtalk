package com.matrix.messenger

import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.di.sharedModule
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

/** TEMPORARY smoke test: run login against matrix.org with fake credentials and print the result. */
fun main(): Unit = runBlocking {
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
