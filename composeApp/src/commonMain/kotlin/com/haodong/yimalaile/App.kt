package com.haodong.yimalaile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.haodong.yimalaile.di.AppComponent
import com.haodong.yimalaile.ui.locale.LocalAppLocale
import com.haodong.yimalaile.ui.navigation.DisclaimerRoute
import com.haodong.yimalaile.ui.navigation.NotificationSettingsRoute
import com.haodong.yimalaile.ui.navigation.OnboardingRoute
import com.haodong.yimalaile.ui.navigation.PeopleRoute
import com.haodong.yimalaile.ui.navigation.PersonDetailRoute
import com.haodong.yimalaile.ui.navigation.PersonEditRoute
import com.haodong.yimalaile.ui.navigation.SettingsRoute
import com.haodong.yimalaile.ui.pages.disclaimer.DisclaimerScreen
import com.haodong.yimalaile.ui.pages.onboarding.OnboardingScreen
import com.haodong.yimalaile.ui.pages.people.PeopleScreen
import com.haodong.yimalaile.ui.pages.people.PersonDetailScreen
import com.haodong.yimalaile.ui.pages.people.PersonEditScreen
import com.haodong.yimalaile.ui.pages.settings.NotificationSettingsScreen
import com.haodong.yimalaile.ui.pages.settings.SettingsScreen
import com.haodong.yimalaile.ui.pages.splash.SplashScreen
import com.haodong.yimalaile.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import yimalaile.composeapp.generated.resources.Res
import yimalaile.composeapp.generated.resources.health_sync_disabled_body
import yimalaile.composeapp.generated.resources.health_sync_off_hint
import yimalaile.composeapp.generated.resources.health_sync_synced
import yimalaile.composeapp.generated.resources.health_sync_unavailable

@Composable
fun App(component: AppComponent) {
    val service = component.menstrualService
    val personRepository = component.personRepository
    val settings = component.settingsRepository
    val notificationService = component.notificationService
    val reportExportService = component.reportExportService
    val healthSyncManager = component.healthSyncManager

    val viewModel = remember {
        AppViewModel(
            service,
            personRepository,
            settings,
            notificationService,
            reportExportService,
        )
    }
    val people by viewModel.people.collectAsState()

    val darkMode = viewModel.darkMode
    val language = viewModel.language
    val startRoute = viewModel.startRoute

    var splashDone by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalAppLocale provides language,
    ) {
        AppTheme(darkMode = darkMode) {
            Box(Modifier.fillMaxSize()) {
                if (startRoute != null) key(language) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = startRoute) {
                        composable<DisclaimerRoute> {
                            DisclaimerScreen(onAccept = {
                                viewModel.setDisclaimerAccepted(true)
                                navController.navigate(OnboardingRoute) {
                                    popUpTo(DisclaimerRoute) { inclusive = true }
                                }
                            })
                        }

                        composable<OnboardingRoute> {
                            OnboardingScreen(
                                service = service,
                                personRepository = personRepository,
                                settings = settings,
                                notificationPrefs = viewModel.notificationPrefs,
                                notificationPermissionGranted = viewModel.notificationPermissionGranted,
                                onUpdateNotificationPrefs = { viewModel.updateNotificationPrefs(it) },
                                onRequestNotificationPermission = { viewModel.requestNotificationPermission() },
                                onComplete = { person ->
                                    viewModel.rescheduleNotifications()
                                    viewModel.refreshPeople()
                                    navController.navigate(
                                        if (person.isForMe) PersonDetailRoute(person.id) else PeopleRoute
                                    ) {
                                        popUpTo(OnboardingRoute) { inclusive = true }
                                    }
                                },
                            )
                        }

                        composable<PeopleRoute> {
                            PeopleScreen(
                                people = people,
                                service = service,
                                onPersonClick = { person -> navController.navigate(PersonDetailRoute(person.id)) },
                                onAddPerson = { navController.navigate(PersonEditRoute(null)) },
                                onSettings = { navController.navigate(SettingsRoute) },
                            )
                        }

                        composable<PersonDetailRoute> { backStackEntry ->
                            val personId = backStackEntry.toRoute<PersonDetailRoute>().personId
                            val person = people.firstOrNull { it.id == personId }
                            if (person != null) {
                                PersonDetailScreen(
                                    service = service,
                                    settings = settings,
                                    person = person,
                                    onBack = { navController.popBackStack() },
                                    onEditPerson = {
                                        navController.navigate(PersonEditRoute(person.id))
                                    },
                                )
                            }
                        }

                        composable<PersonEditRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<PersonEditRoute>()
                            val existingPerson = route.personId?.let { id -> people.firstOrNull { it.id == id } }
                            var healthMessage by remember(existingPerson?.id) { mutableStateOf<String?>(null) }
                            var healthCounts by remember(existingPerson?.id) { mutableStateOf<Pair<Int, Int>?>(null) }
                            val scope = rememberCoroutineScope()
                            val msgUnavailable = stringResource(Res.string.health_sync_unavailable)
                            val msgOff = stringResource(Res.string.health_sync_off_hint)
                            val msgOther = stringResource(Res.string.health_sync_disabled_body)
                            val counts = healthCounts
                            val syncedMsg = counts?.let { stringResource(Res.string.health_sync_synced, it.first, it.second) }
                            PersonEditScreen(
                                service = service,
                                personRepository = personRepository,
                                person = existingPerson,
                                reportExportService = reportExportService,
                                healthSyncEnabled = viewModel.healthSyncEnabled,
                                onHealthSyncChange = { viewModel.updateHealthSync(it) },
                                onHealthSyncNow = if (existingPerson?.isForMe == true) {
                                    {
                                        scope.launch {
                                            when (val r = healthSyncManager.sync(existingPerson)) {
                                                is com.haodong.yimalaile.domain.health.HealthSyncResult.Success -> {
                                                    healthCounts = r.imported to r.exported
                                                    healthMessage = null
                                                }
                                                is com.haodong.yimalaile.domain.health.HealthSyncResult.Unavailable -> {
                                                    healthCounts = null
                                                    healthMessage = r.reason.ifEmpty { msgUnavailable }
                                                }
                                                com.haodong.yimalaile.domain.health.HealthSyncResult.DisabledByUser -> {
                                                    healthCounts = null
                                                    healthMessage = msgOff
                                                }
                                                com.haodong.yimalaile.domain.health.HealthSyncResult.DisabledForOtherPerson -> {
                                                    healthCounts = null
                                                    healthMessage = msgOther
                                                }
                                            }
                                        }
                                    }
                                } else null,
                                healthSyncMessage = syncedMsg ?: healthMessage,
                                onBack = { navController.popBackStack() },
                                onSaved = {
                                    viewModel.refreshPeople()
                                    navController.popBackStack(PeopleRoute, false)
                                },
                            )
                        }

                        composable<SettingsRoute> {
                            SettingsScreen(
                                currentDarkMode = darkMode,
                                onDarkModeChange = { newMode -> viewModel.updateDarkMode(newMode) },
                                onBack = { navController.popBackStack() },
                                onClearData = {
                                    viewModel.clearAllData()
                                    navController.navigate(DisclaimerRoute) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onNavigateNotifications = { navController.navigate(NotificationSettingsRoute) },
                            )
                        }

                        composable<NotificationSettingsRoute> {
                            NotificationSettingsScreen(
                                prefs = viewModel.notificationPrefs,
                                hasPermission = viewModel.notificationPermissionGranted,
                                onRequestPermission = { viewModel.requestNotificationPermission() },
                                onPrefsChange = { viewModel.updateNotificationPrefs(it) },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }

                // Brand splash overlays everything until upstream state is
                // loaded and the entrance animation has settled. Fades out
                // smoothly so the underlying NavHost is already mounted.
                AnimatedVisibility(
                    visible = !splashDone,
                    enter = EnterTransition.None,
                    exit = fadeOut(tween(450)),
                ) {
                    SplashScreen(
                        ready = startRoute != null,
                        onFinish = { splashDone = true },
                    )
                }
            }
        }
    }
}