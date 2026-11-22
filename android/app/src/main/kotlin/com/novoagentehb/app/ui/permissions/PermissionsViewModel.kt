package com.novoagentehb.app.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.bridges.AssistantAccessibilityService
import com.novoagentehb.app.bridges.NotificationFeedService
import com.novoagentehb.app.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionsSnapshot(
    val recordAudio: Boolean = false,
    val notifications: Boolean = false,
    val contacts: Boolean = false,
    val callPhone: Boolean = false,
    val calendarRead: Boolean = false,
    val calendarWrite: Boolean = false,
    val notificationListener: Boolean = false,
    val accessibility: Boolean = false,
    val exactAlarm: Boolean = true
)

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) : ViewModel() {

    val onboardingCompleted: Flow<Boolean> = prefs.onboardingCompleted

    private val _snapshot = MutableStateFlow(PermissionsSnapshot())
    val snapshot: StateFlow<PermissionsSnapshot> = _snapshot.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val notifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        } else true
        val exactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else true

        _snapshot.value = PermissionsSnapshot(
            recordAudio = hasPerm(Manifest.permission.RECORD_AUDIO),
            notifications = notifPerm,
            contacts = hasPerm(Manifest.permission.READ_CONTACTS),
            callPhone = hasPerm(Manifest.permission.CALL_PHONE),
            calendarRead = hasPerm(Manifest.permission.READ_CALENDAR),
            calendarWrite = hasPerm(Manifest.permission.WRITE_CALENDAR),
            notificationListener = NotificationFeedService.isEnabled(context),
            accessibility = AssistantAccessibilityService.isEnabled.value,
            exactAlarm = exactAlarm
        )
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefs.setOnboardingCompleted(true)
        }
    }

    private fun hasPerm(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
