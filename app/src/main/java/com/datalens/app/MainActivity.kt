package com.datalens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.datalens.app.domain.model.UiSettings
import com.datalens.app.notifications.TrackingController
import com.datalens.app.ui.navigation.DataLensNavHost
import com.datalens.app.ui.navigation.Routes
import com.datalens.app.ui.theme.DataLensTheme
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        // Re-align the usage-tracking foreground service with the persisted
        // setting — recovers from system/user kills without any extra polling.
        lifecycleScope.launch { TrackingController.ensureStartedIfEnabled(this@MainActivity) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by ServiceLocator.settingsRepository.uiSettings
                .collectAsStateWithLifecycle(initialValue = UiSettings())
            DataLensTheme(
                themeMode = settings.theme,
                dynamicColors = settings.dynamicColors,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DataLensAppRoot()
                }
            }
        }
    }
}

/**
 * Decides where the app starts: the Overview dashboard when Usage Access is already
 * granted, otherwise the onboarding/permission screen.
 */
@Composable
private fun DataLensAppRoot() {
    val context = LocalContext.current
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = if (UsageAccess.isGranted(context)) Routes.OVERVIEW else Routes.ONBOARDING
    }

    when (val destination = startDestination) {
        null -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "DataLens", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator()
            }
        }
        else -> DataLensNavHost(startDestination = destination)
    }
}
