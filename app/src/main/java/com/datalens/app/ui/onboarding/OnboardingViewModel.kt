package com.datalens.app.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import com.datalens.app.util.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OnboardingViewModel(appContext: Context) : ViewModel() {

    private val appContext = appContext.applicationContext

    private val _permissionGranted = MutableStateFlow(UsageAccess.isGranted(appContext))
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    /** Cheap AppOps check — safe to call repeatedly while the user is in Settings. */
    fun checkPermission() {
        val granted = UsageAccess.isGranted(appContext)
        if (_permissionGranted.value != granted) {
            _permissionGranted.value = granted
        }
    }
}
