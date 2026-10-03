package com.datalens.app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datalens.app.R
import com.datalens.app.ServiceLocator
import kotlinx.coroutines.delay

/**
 * Permission onboarding. PACKAGE_USAGE_STATS cannot be requested via a runtime
 * dialog — the user must grant "Usage access" in Android's special settings, so we
 * explain why it's needed, what DataLens *never* does, and detect the grant as soon
 * as the user returns.
 */
@Composable
fun OnboardingScreen(
    onGranted: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(
        initializer = { OnboardingViewModel(ServiceLocator.appContext) },
    ),
) {
    val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Poll while the user is in system settings, then continue automatically.
    LaunchedEffect(permissionGranted) {
        while (!permissionGranted) {
            delay(1500)
            viewModel.checkPermission()
        }
        onGranted()
    }
    if (permissionGranted) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(140.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("DataLens", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "See which apps use your mobile data",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Usage Access required",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "DataLens needs Usage Access to read Android's app usage statistics " +
                        "and calculate mobile data usage per app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Android only exposes per-app network statistics to apps with Usage " +
                        "Access — it is granted in Android's special settings, not via a " +
                        "permission pop-up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("What DataLens does", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PrivacyPoint(true, "Reads only Android-provided usage statistics")
                PrivacyPoint(true, "Keeps everything on this device — no internet access at all")
                Spacer(Modifier.height(8.dp))
                Text("What DataLens never does", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PrivacyPoint(false, "Reads your messages")
                PrivacyPoint(false, "Inspects passwords")
                PrivacyPoint(false, "Inspects website contents")
                PrivacyPoint(false, "Captures network packets")
                PrivacyPoint(false, "Uploads your network activity")
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    // No usage-access settings on this device — keep showing guidance.
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Grant Usage Access")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "You'll return here automatically once access is granted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PrivacyPoint(positive: Boolean, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (positive) Icons.Filled.CheckCircle else Icons.Filled.Close,
            contentDescription = null,
            tint = if (positive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
