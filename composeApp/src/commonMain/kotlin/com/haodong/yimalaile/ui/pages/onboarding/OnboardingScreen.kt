package com.haodong.yimalaile.ui.pages.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haodong.yimalaile.domain.menstrual.ENOUGH_RECORDS_FOR_PREDICTION
import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.notifications.NotificationPrefs
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.person.PersonRepository
import com.haodong.yimalaile.domain.settings.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.until

/** Visual position of each onboarding step (used for the indicator and back nav). */
internal enum class OnboardingStep {
    Welcome,
    Purpose,
    PeriodStatus,
    CycleSettings,
    Notifications,
    AllSet,
}

@Composable
fun OnboardingScreen(
    service: MenstrualService,
    personRepository: PersonRepository,
    settings: SettingsRepository,
    notificationPrefs: NotificationPrefs,
    notificationPermissionGranted: Boolean,
    onUpdateNotificationPrefs: (NotificationPrefs) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onComplete: (Person) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(OnboardingStep.Welcome) }

    // Person identity
    var personName by remember { mutableStateOf("") }
    var isForMe by remember { mutableStateOf<Boolean?>(null) }
    var createdPerson by remember { mutableStateOf<Person?>(null) }

    // Period-status state
    var statusOption by remember { mutableStateOf<PeriodStatusOption?>(null) }
    var activeStart by remember { mutableStateOf<LocalDate?>(null) }
    var endedStart by remember { mutableStateOf<LocalDate?>(null) }
    var endedEnd by remember { mutableStateOf<LocalDate?>(null) }

    // Cycle settings state
    var cycleLength by remember { mutableIntStateOf(28) }
    var periodDuration by remember { mutableIntStateOf(5) }

    val orderedSteps: List<OnboardingStep> = buildList {
        add(OnboardingStep.Welcome)
        add(OnboardingStep.Purpose)
        add(OnboardingStep.PeriodStatus)
        add(OnboardingStep.CycleSettings)
        add(OnboardingStep.Notifications)
        add(OnboardingStep.AllSet)
    }

    val totalProgressSteps = orderedSteps.count { it != OnboardingStep.Welcome && it != OnboardingStep.AllSet }
    val currentProgressIndex = orderedSteps
        .filter { it != OnboardingStep.Welcome && it != OnboardingStep.AllSet }
        .indexOf(step)
        .let { if (it < 0) 0 else it + 1 }

    fun goNext() {
        val idx = orderedSteps.indexOf(step)
        if (idx >= 0 && idx + 1 < orderedSteps.size) step = orderedSteps[idx + 1]
    }

    fun goBack() {
        val idx = orderedSteps.indexOf(step)
        if (idx > 0) step = orderedSteps[idx - 1]
    }

    fun resolvePeriodEntry(): PeriodEntry = when (statusOption) {
        PeriodStatusOption.Active -> activeStart?.let { PeriodEntry.Active(it) } ?: PeriodEntry.None
        PeriodStatusOption.Ended -> {
            val s = endedStart
            val e = endedEnd
            if (s != null && e != null && e >= s) PeriodEntry.Ended(s, e) else PeriodEntry.None
        }
        PeriodStatusOption.Unsure, null -> PeriodEntry.None
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = step != OnboardingStep.Welcome && step != OnboardingStep.AllSet,
            ) {
                StepIndicator(
                    current = currentProgressIndex,
                    total = totalProgressSteps,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = orderedSteps.indexOf(targetState) >= orderedSteps.indexOf(initialState)
                    val sign = if (forward) 1 else -1
                    (slideInHorizontally(tween(280)) { sign * it / 6 } + fadeIn(tween(280)))
                        .togetherWith(
                            slideOutHorizontally(tween(220)) { -sign * it / 6 } + fadeOut(tween(220))
                        )
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onboarding-step",
            ) { current ->
                when (current) {
                    OnboardingStep.Welcome -> WelcomeStep(onNext = { goNext() })

                    OnboardingStep.Purpose -> PurposeStep(
                        selected = isForMe,
                        onSelect = { isForMe = it },
                        onContinue = { goNext() },
                        onBack = { goBack() },
                    )

                    OnboardingStep.PeriodStatus -> PeriodStatusStep(
                        isForMe = isForMe == true,
                        selected = statusOption,
                        activeStart = activeStart,
                        endedStart = endedStart,
                        endedEnd = endedEnd,
                        onSelect = { statusOption = it },
                        onActiveStartChange = { activeStart = it },
                        onEndedStartChange = {
                            endedStart = it
                            if (endedEnd != null && endedEnd!! < it) endedEnd = null
                        },
                        onEndedEndChange = { endedEnd = it },
                        onContinue = {
                            // Pre-fill the cycle-settings duration slider from the Ended range
                            // so the user sees a sensible default instead of the generic 5.
                            if (statusOption == PeriodStatusOption.Ended) {
                                val s = endedStart
                                val e = endedEnd
                                if (s != null && e != null) {
                                    periodDuration = s.until(e, DateTimeUnit.DAY).toInt() + 1
                                }
                            }
                            goNext()
                        },
                        onBack = { goBack() },
                    )

                    OnboardingStep.CycleSettings -> {
                        val plannedCount = when (statusOption) {
                            PeriodStatusOption.Active, PeriodStatusOption.Ended -> 1
                            PeriodStatusOption.Unsure, null -> 0
                        }
                        CycleSettingsStep(
                            isForMe = isForMe == true,
                            personName = personName,
                            onPersonNameChange = { personName = it },
                            cycleLength = cycleLength,
                            periodDuration = periodDuration,
                            onCycleLengthChange = { cycleLength = it },
                            onPeriodDurationChange = { periodDuration = it },
                            showSyntheticHint = plannedCount < ENOUGH_RECORDS_FOR_PREDICTION,
                            onSave = { goNext() },
                            onBack = { goBack() },
                        )
                    }

                    OnboardingStep.Notifications -> NotificationsStep(
                        prefs = notificationPrefs,
                        hasPermission = notificationPermissionGranted,
                        onPrefsChange = onUpdateNotificationPrefs,
                        onRequestPermission = onRequestNotificationPermission,
                        onContinue = {
                            scope.launch {
                                if (createdPerson == null) {
                                    createdPerson = commitOnboarding(
                                        service = service,
                                        personRepository = personRepository,
                                        personName = personName,
                                        isForMe = isForMe == true,
                                        cycleLength = cycleLength,
                                        periodDuration = periodDuration,
                                        entry = resolvePeriodEntry(),
                                    )
                                }
                                goNext()
                            }
                        },
                        onSkip = {
                            scope.launch {
                                if (createdPerson == null) {
                                    createdPerson = commitOnboarding(
                                        service = service,
                                        personRepository = personRepository,
                                        personName = personName,
                                        isForMe = isForMe == true,
                                        cycleLength = cycleLength,
                                        periodDuration = periodDuration,
                                        entry = resolvePeriodEntry(),
                                    )
                                }
                                goNext()
                            }
                        },
                        onBack = { goBack() },
                    )

                    OnboardingStep.AllSet -> AllSetStep(
                        isForMe = isForMe == true,
                        onComplete = { createdPerson?.let(onComplete) },
                    )
                }
            }
        }
    }
}