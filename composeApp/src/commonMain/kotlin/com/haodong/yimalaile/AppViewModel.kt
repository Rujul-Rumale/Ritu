package com.haodong.yimalaile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.haodong.yimalaile.domain.export.ExportResult
import com.haodong.yimalaile.domain.export.ReportExportService
import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.notifications.NotificationPrefs
import com.haodong.yimalaile.domain.notifications.NotificationService
import com.haodong.yimalaile.domain.notifications.buildNotificationCopy
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.person.PersonRepository
import com.haodong.yimalaile.domain.settings.AppDarkMode
import com.haodong.yimalaile.domain.settings.SettingsRepository
import com.haodong.yimalaile.ui.navigation.DisclaimerRoute
import com.haodong.yimalaile.ui.navigation.OnboardingRoute
import com.haodong.yimalaile.ui.navigation.PeopleRoute
import com.haodong.yimalaile.ui.navigation.PersonDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(
    private val service: MenstrualService,
    private val personRepository: PersonRepository,
    private val settings: SettingsRepository,
    private val notificationService: NotificationService? = null,
    private val reportExportService: ReportExportService? = null,
) : ViewModel() {

    var darkMode by mutableStateOf(AppDarkMode.SYSTEM)
        private set

    var language = "en"
        private set

    var startRoute by mutableStateOf<Any?>(null)
        private set

    var notificationPrefs by mutableStateOf(NotificationPrefs())
        private set

    var notificationPermissionGranted by mutableStateOf(false)
        private set

    var healthSyncEnabled by mutableStateOf(false)
        private set

    var exportStatus by mutableStateOf<ExportStatus>(ExportStatus.Idle)
        private set

    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    fun refreshPeople() {
        viewModelScope.launch { _people.value = personRepository.getPeople() }
    }

    init {
        viewModelScope.launch {
            darkMode = settings.getDarkMode()
            notificationPrefs = settings.getNotificationPrefs()
            notificationPermissionGranted = notificationService?.hasPermission() ?: false
            healthSyncEnabled = settings.isHealthSyncEnabled()

            val people = personRepository.getPeople()
            _people.value = people
            val disclaimerAccepted = settings.isDisclaimerAccepted()
            startRoute = when {
                !disclaimerAccepted -> DisclaimerRoute
                people.isEmpty() -> OnboardingRoute
                // A lone "For me" person lands on their own home directly; the
                // people list stays reachable via its own entry point.
                people.size == 1 && people[0].isForMe -> PersonDetailRoute(people[0].id)
                else -> PeopleRoute
            }

            // Re-apply the stored schedule on app start so alarms survive
            // app relaunch and preference bundle migrations.
            applySchedule()
        }
    }

    fun updateDarkMode(mode: AppDarkMode) {
        viewModelScope.launch {
            darkMode = mode
            settings.setDarkMode(mode)
        }
    }

    fun setDisclaimerAccepted(accepted: Boolean) {
        viewModelScope.launch {
            settings.setDisclaimerAccepted(accepted)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            service.clearAllData()
            settings.setDisclaimerAccepted(false)
            notificationService?.cancelAll()
        }
    }

    fun updateNotificationPrefs(prefs: NotificationPrefs) {
        viewModelScope.launch {
            val wasEnabled = notificationPrefs.periodReminderEnabled ||
                notificationPrefs.ovulationReminderEnabled ||
                notificationPrefs.dailyReportEnabled
            val isEnabled = prefs.periodReminderEnabled ||
                prefs.ovulationReminderEnabled ||
                prefs.dailyReportEnabled

            notificationPrefs = prefs
            settings.setNotificationPrefs(prefs)

            // If the user is turning a reminder ON for the first time and we
            // don't yet have OS-level permission, prompt them now. Without
            // this the alarm is scheduled silently and nothing ever shows up.
            if (!wasEnabled && isEnabled && !notificationPermissionGranted) {
                val granted = notificationService?.requestPermission() ?: false
                notificationPermissionGranted = granted
            }

            applySchedule()
        }
    }

    /**
     * Global health-sync toggle. The per-person gate lives in
     * HealthSyncGate: even when this is on, only Person.isForMe may sync.
     */
    fun updateHealthSync(enabled: Boolean) {
        viewModelScope.launch {
            healthSyncEnabled = enabled
            settings.setHealthSyncEnabled(enabled)
        }
    }

    /**
     * Render the full history of one person to a long PNG image and save it to
     * the device's Pictures folder.
     */
    fun exportReport(personId: String, language: String) {
        val exporter = reportExportService ?: run {
            exportStatus = ExportStatus.Failure("Export service not available")
            return
        }
        viewModelScope.launch {
            exportStatus = ExportStatus.InProgress
            val records = service.getAllRecords(personId)
            exportStatus = when (val result = exporter.exportCycleReport(records, language)) {
                is ExportResult.Success -> ExportStatus.Success(result.displayLocation)
                is ExportResult.Failure -> ExportStatus.Failure(result.message)
            }
        }
    }

    fun resetExportStatus() {
        exportStatus = ExportStatus.Idle
    }

    fun requestNotificationPermission() {
        viewModelScope.launch {
            val granted = notificationService?.requestPermission() ?: false
            notificationPermissionGranted = granted
            if (granted) applySchedule()
        }
    }

    /** Public trigger for rescheduling after cycle data changes. */
    fun rescheduleNotifications() {
        viewModelScope.launch { applySchedule() }
    }

    private suspend fun applySchedule() {
        val svc = notificationService ?: return
        val prefs = notificationPrefs
        svc.reschedule(prefs = prefs, copy = buildNotificationCopy(prefs))
    }
}

sealed class ExportStatus {
    data object Idle : ExportStatus()
    data object InProgress : ExportStatus()
    data class Success(val location: String) : ExportStatus()
    data class Failure(val message: String) : ExportStatus()
}