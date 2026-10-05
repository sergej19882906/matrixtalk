package com.matrix.messenger.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.receiver.OemBatteryHelper
import com.matrix.messenger.ui.navigation.AppNavigation
import com.matrix.messenger.ui.navigation.Screen
import com.matrix.messenger.ui.theme.MatrixMessengerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val matrixRepository: MatrixRepository by inject()

    private var isReady by mutableStateOf(false)
    private var isAuthenticated by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            try {
                matrixRepository.initialize()
                isAuthenticated = matrixRepository.currentUser.first() != null
                isReady = true
                if (isAuthenticated) {
                    requestBatteryBypassIfNeeded()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize", e)
                isReady = true
            }
        }

        setContent {
            MatrixMessengerTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .windowInsetsPadding(WindowInsets.displayCutout),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    if (isReady) {
                        AppNavigation(
                            navController = navController,
                            startDestination = if (isAuthenticated) {
                                Screen.Home.route
                            } else {
                                Screen.Login.route
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestBatteryBypassIfNeeded() {
        try {
            window.decorView.post {
                OemBatteryHelper.requestBatteryOptimizationBypass(this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Battery bypass request failed", e)
        }
    }
}
