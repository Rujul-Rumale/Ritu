package com.haodong.yimalaile.ui.pages.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.haodong.yimalaile.domain.menstrual.AddRecordResult
import com.haodong.yimalaile.domain.menstrual.CyclePhaseInfo
import com.haodong.yimalaile.domain.menstrual.CycleState
import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.ui.components.DecorShape
import com.haodong.yimalaile.ui.components.SmallSpacer
import com.haodong.yimalaile.ui.components.SuccessOverlay
import com.haodong.yimalaile.ui.pages.sheet.SheetViewModel
import com.haodong.yimalaile.ui.theme.expressiveShapes
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import yimalaile.composeapp.generated.resources.Res
import yimalaile.composeapp.generated.resources.record_save_success

@Composable
fun HomeScreen(
    service: MenstrualService,
    sheetViewModel: SheetViewModel,
    person: Person,
    settings: com.haodong.yimalaile.domain.settings.SettingsRepository,
    onNavigateSettings: () -> Unit,
    onNavigatePeople: () -> Unit,
) {
    val viewModel = remember { HomeViewModel(service, settings, person) }
    val cycleState = viewModel.cycleState
    val phaseInfo = viewModel.phaseInfo
    val isLoading = viewModel.isLoading
    val homeMode = viewModel.homeMode
    val scope = rememberCoroutineScope()
    val successMsg = stringResource(Res.string.record_save_success)
    var successMessage by remember { mutableStateOf<String?>(null) }
    // Selected date on the detail calendar — drives the default for "姨妈来了" when
    // the user has tapped a recent day on the calendar before pressing the button.
    var detailSelectedDate by remember { mutableStateOf<LocalDate?>(null) }

    LaunchedEffect(Unit) {
        sheetViewModel.dataChanged.collect { viewModel.refresh() }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        if (!isLoading && cycleState != null) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    HomeAppBar(
                        personName = person.name,
                        onNavigateSettings = onNavigateSettings,
                        onNavigatePeople = onNavigatePeople,
                    )

                    HomeContent(
                        homeMode = homeMode,
                        cycleState = cycleState,
                        phaseInfo = phaseInfo,
                        service = service,
                        personId = person.id,
                        onRefresh = { viewModel.refresh() },
                        sheetViewModel = sheetViewModel,
                        detailSelectedDate = detailSelectedDate,
                        onDetailSelectedDateChange = { detailSelectedDate = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Floating bottom toolbar ──
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                ) {
                    BottomSection(
                        inPeriod = cycleState.inPeriod,
                        homeMode = homeMode,
                        onToggleMode = { mode -> viewModel.updateHomeMode(mode) },
                        onPeriodArrived = {
                            scope.launch {
                                val result = sheetViewModel.recordPeriodStart(
                                    preferredStart = detailSelectedDate,
                                ) ?: return@launch
                                if (result is AddRecordResult.Success) {
                                    viewModel.refresh(); successMessage = successMsg
                                }
                            }
                        },
                        onPeriodGone = {
                            scope.launch {
                                val ok = sheetViewModel.recordPeriodEnd() ?: return@launch
                                if (ok) {
                                    viewModel.refresh(); successMessage = successMsg
                                }
                            }
                        },
                    )
                }
            }
        }

        SuccessOverlay(
            message = successMessage,
            onDismiss = { successMessage = null },
        )
    }
}

@Composable
private fun HomeAppBar(
    personName: String,
    onNavigateSettings: () -> Unit,
    onNavigatePeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigatePeople, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DecorShape(
                24,
                shape = MaterialTheme.expressiveShapes.cookie7,
                color = MaterialTheme.colorScheme.primary
            )
            SmallSpacer(8)
            Text(
                personName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onNavigateSettings,
            modifier = Modifier.testTag("nav_settings"),
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeContent(
    homeMode: HomeMode,
    cycleState: CycleState,
    phaseInfo: CyclePhaseInfo?,
    service: MenstrualService,
    personId: String,
    onRefresh: () -> Unit,
    sheetViewModel: SheetViewModel,
    detailSelectedDate: LocalDate?,
    onDetailSelectedDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = homeMode,
        modifier = modifier,
        transitionSpec = {
            val fromIdx = initialState.ordinal
            val toIdx = targetState.ordinal
            val direction = if (toIdx > fromIdx) 1 else -1
            (fadeIn(tween(300)) + slideIn(tween(300)) { IntOffset(direction * it.width / 6, 0) })
                .togetherWith(fadeOut(tween(200)) + slideOut(tween(200)) {
                    IntOffset(-direction * it.width / 6, 0)
                })
        },
        label = "home_mode",
    ) { mode ->
        when (mode) {
            HomeMode.CALENDAR -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    HomeCalendar(
                        state = cycleState,
                        phaseInfo = phaseInfo,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            HomeMode.DETAIL -> {
                DetailCalendarView(
                    cycleState = cycleState,
                    phaseInfo = phaseInfo,
                    service = service,
                    personId = personId,
                    onRefresh = onRefresh,
                    selectedDate = detailSelectedDate,
                    onSelectedDateChange = onDetailSelectedDateChange,
                )
            }
            HomeMode.STATS -> {
                HomeStatistics(
                    cycleState = cycleState,
                    sheetViewModel = sheetViewModel,
                    onRefresh = onRefresh,
                )
            }
        }
    }
}
