package com.matrix.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.rememberNavController
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.di.sharedModule
import com.matrix.messenger.media.setupImageLoader
import com.matrix.messenger.ui.navigation.AppNavigation
import com.matrix.messenger.ui.navigation.Screen
import com.matrix.messenger.ui.theme.MatrixDarkGray
import com.matrix.messenger.ui.theme.MatrixMessengerTheme
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.koin.core.context.startKoin

fun main() {
    installDesktopErrorLogging()
    startKoin {
        modules(sharedModule)
    }
    setupImageLoader()

    application {
    val matrixRepository: MatrixRepository = koinInject()
    var isReady by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf(Screen.Login.route) }

    LaunchedEffect(Unit) {
        try {
            matrixRepository.initialize()
            if (matrixRepository.currentUser.first() != null) {
                startDestination = Screen.Home.route
            }
        } catch (t: Throwable) {
            logDesktopError(t)
        } finally {
            isReady = true
        }
    }

    // The window must exist from the first composition: Compose Desktop's
    // `application` exits when no window is present, so gating Window creation
    // on async state silently terminated the app on launch.
    Window(
        onCloseRequest = ::exitApplication,
        title = "MatrixTalk",
        state = rememberWindowState()
    ) {
        if (isReady) {
            MatrixMessengerTheme {
                Box(modifier = Modifier.fillMaxSize().background(MatrixDarkGray)) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
    }
}

private fun installDesktopErrorLogging() {
    val dir = java.io.File(
        System.getProperty("user.home") ?: return,
        ".matrixtalk/logs"
    ).apply { mkdirs() }
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching {
            dir.resolve("error.log").appendText(
                "${java.time.LocalDateTime.now()} [${thread.name}] ${throwable.stackTraceToString()}\n\n"
            )
        }
        throwable.printStackTrace()
    }
}

private fun logDesktopError(t: Throwable) {
    runCatching {
        val dir = java.io.File(System.getProperty("user.home"), ".matrixtalk/logs").apply { mkdirs() }
        dir.resolve("error.log").appendText(
            "${java.time.LocalDateTime.now()} [init] ${t.stackTraceToString()}\n\n"
        )
    }
    t.printStackTrace()
}
