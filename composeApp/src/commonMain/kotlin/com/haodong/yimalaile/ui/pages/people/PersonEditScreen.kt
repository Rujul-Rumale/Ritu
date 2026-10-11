package com.haodong.yimalaile.ui.pages.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haodong.yimalaile.ExportStatus
import com.haodong.yimalaile.domain.export.ExportResult
import com.haodong.yimalaile.domain.export.ReportExportService
import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.person.DEFAULT_PERSON_NAME
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.domain.person.PersonRepository
import com.haodong.yimalaile.ui.components.PrimaryCta
import com.haodong.yimalaile.ui.components.SmallSpacer
import com.haodong.yimalaile.ui.pages.onboarding.ValueSlider
import com.haodong.yimalaile.ui.pages.settings.ExportDialog
import com.haodong.yimalaile.ui.pages.sheet.sheets.GenericDatePickerSheet
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import yimalaile.composeapp.generated.resources.Res
import yimalaile.composeapp.generated.resources.detail_delete_confirm
import yimalaile.composeapp.generated.resources.dialog_cancel
import yimalaile.composeapp.generated.resources.health_sync_disabled_body
import yimalaile.composeapp.generated.resources.health_sync_disabled_title
import yimalaile.composeapp.generated.resources.health_sync_subtitle_me
import yimalaile.composeapp.generated.resources.health_sync_sync_now
import yimalaile.composeapp.generated.resources.health_sync_title
import yimalaile.composeapp.generated.resources.health_sync_toggle
import yimalaile.composeapp.generated.resources.onboarding_cycle_length_label
import yimalaile.composeapp.generated.resources.onboarding_period_duration_label
import yimalaile.composeapp.generated.resources.onboarding_person_name_hint
import yimalaile.composeapp.generated.resources.onboarding_person_name_label
import yimalaile.composeapp.generated.resources.onboarding_save
import yimalaile.composeapp.generated.resources.person_add_end_label
import yimalaile.composeapp.generated.resources.person_add_end_pick
import yimalaile.composeapp.generated.resources.person_add_option_dates
import yimalaile.composeapp.generated.resources.person_add_option_ongoing
import yimalaile.composeapp.generated.resources.person_add_option_today
import yimalaile.composeapp.generated.resources.person_add_option_unsure
import yimalaile.composeapp.generated.resources.person_add_period_title
import yimalaile.composeapp.generated.resources.person_add_start_label
import yimalaile.composeapp.generated.resources.person_add_start_pick
import yimalaile.composeapp.generated.resources.person_add_title
import yimalaile.composeapp.generated.resources.person_add_unsure_hint
import yimalaile.composeapp.generated.resources.person_delete_body
import yimalaile.composeapp.generated.resources.person_delete_title
import yimalaile.composeapp.generated.resources.person_edit_this_is_me
import yimalaile.composeapp.generated.resources.person_edit_this_is_me_hint
import yimalaile.composeapp.generated.resources.person_edit_title
import yimalaile.composeapp.generated.resources.settings_export
import yimalaile.composeapp.generated.resources.unit_days

/** How the user answers "when was their last period?" when adding a person. */
private enum class AddPeriodOption { Today, Dates, Unsure }

private enum class PickerTarget { Start, End }

/**
 * Create ([person] == null) or edit (existing) a tracked person.
 *
 * For a brand-new person we only ask the essential question — when did their
 * last period happen (today, explicit dates, or not sure) — then pad the rest
 * of the history from the cycle sliders so predictions work immediately.
 */
@Composable
fun PersonEditScreen(
    service: MenstrualService,
    personRepository: PersonRepository,
    person: Person?,
    reportExportService: ReportExportService? = null,
    healthSyncEnabled: Boolean = false,
    onHealthSyncChange: (Boolean) -> Unit = {},
    onHealthSyncNow: (() -> Unit)? = null,
    healthSyncMessage: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isEditing = person != null

    var personName by remember { mutableStateOf(person?.name.orEmpty()) }
    var periodLength by remember { mutableIntStateOf(person?.periodLength ?: 5) }
    var cycleLength by remember { mutableIntStateOf(person?.cycleLength ?: 28) }
    var isForMe by remember { mutableStateOf(person?.isForMe ?: false) }

    // Add-person period question
    var periodOption by remember { mutableStateOf<AddPeriodOption?>(null) }
    var lastStart by remember { mutableStateOf<LocalDate?>(null) }
    var lastEnd by remember { mutableStateOf<LocalDate?>(null) }
    var stillOngoing by remember { mutableStateOf(true) }
    var openPicker by remember { mutableStateOf<PickerTarget?>(null) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<ExportStatus>(ExportStatus.Idle) }

    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val earliest = remember(today) { today.minus(6, DateTimeUnit.MONTH) }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(if (isEditing) Res.string.person_edit_title else Res.string.person_add_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp))
        }

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = if (isForMe) DEFAULT_PERSON_NAME else personName,
            onValueChange = { personName = it.take(30) },
            enabled = !isForMe,
            label = { Text(stringResource(Res.string.onboarding_person_name_label)) },
            placeholder = { Text(stringResource(Res.string.onboarding_person_name_hint)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        SmallSpacer(8)

        // "This is me" — the app owner's own profile. First-person copy and
        // single-person home routing key off this flag.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Face,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.person_edit_this_is_me),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(Res.string.person_edit_this_is_me_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isForMe,
                onCheckedChange = {
                    isForMe = it
                    if (it) personName = DEFAULT_PERSON_NAME
                },
            )
        }

        if (!isEditing) {
            SmallSpacer(28)

            Text(
                stringResource(Res.string.person_add_period_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            SmallSpacer(12)

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = periodOption == AddPeriodOption.Today,
                    onClick = {
                        periodOption = AddPeriodOption.Today
                        lastStart = null
                        lastEnd = null
                        stillOngoing = true
                    },
                    label = { Text(stringResource(Res.string.person_add_option_today)) },
                )
                FilterChip(
                    selected = periodOption == AddPeriodOption.Dates,
                    onClick = {
                        periodOption = AddPeriodOption.Dates
                        stillOngoing = true
                    },
                    label = { Text(stringResource(Res.string.person_add_option_dates)) },
                )
                FilterChip(
                    selected = periodOption == AddPeriodOption.Unsure,
                    onClick = {
                        periodOption = AddPeriodOption.Unsure
                        lastStart = null
                        lastEnd = null
                        stillOngoing = true
                    },
                    label = { Text(stringResource(Res.string.person_add_option_unsure)) },
                )
            }

            when (periodOption) {
                AddPeriodOption.Dates -> {
                    SmallSpacer(16)
                    DateRow(
                        label = stringResource(Res.string.person_add_start_label),
                        value = lastStart?.let { formatDate(it) }
                            ?: stringResource(Res.string.person_add_start_pick),
                        hasValue = lastStart != null,
                        onClick = { openPicker = PickerTarget.Start },
                    )
                    SmallSpacer(12)
                    FilterChip(
                        selected = stillOngoing,
                        onClick = { stillOngoing = !stillOngoing },
                        label = { Text(stringResource(Res.string.person_add_option_ongoing)) },
                    )
                    if (!stillOngoing) {
                        SmallSpacer(8)
                        DateRow(
                            label = stringResource(Res.string.person_add_end_label),
                            value = lastEnd?.let { formatDate(it) }
                                ?: stringResource(Res.string.person_add_end_pick),
                            hasValue = lastEnd != null,
                            enabled = lastStart != null,
                            onClick = { openPicker = PickerTarget.End },
                        )
                    }
                }
                AddPeriodOption.Unsure -> {
                    SmallSpacer(16)
                    Text(
                        stringResource(Res.string.person_add_unsure_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    )
                }
                AddPeriodOption.Today, null -> Unit
            }
        }

        SmallSpacer(28)

        ValueSlider(
            label = stringResource(Res.string.onboarding_period_duration_label),
            value = periodLength,
            valueRange = 2f..10f,
            unit = stringResource(Res.string.unit_days),
            onValueChange = { periodLength = it },
            minLabel = "2",
            maxLabel = "10",
        )
        SmallSpacer(32)
        ValueSlider(
            label = stringResource(Res.string.onboarding_cycle_length_label),
            value = cycleLength,
            valueRange = 20f..45f,
            unit = stringResource(Res.string.unit_days),
            onValueChange = { cycleLength = it },
            minLabel = "20",
            maxLabel = "45",
        )

        SmallSpacer(28)

        // ── Health data sync: For-Me only ──
        // Platform health stores (Health Connect / HealthKit) are single-user,
        // so only the owner's own profile may sync. Other people stay
        // local-only; show the section disabled with the reason.
        HealthSyncSection(
            isForMe = isForMe,
            enabled = healthSyncEnabled,
            message = healthSyncMessage,
            onToggle = onHealthSyncChange,
            onSyncNow = onHealthSyncNow,
        )

        Spacer(Modifier.height(48.dp))

        val canSave = isEditing || when (periodOption) {
            null -> false
            AddPeriodOption.Today, AddPeriodOption.Unsure -> true
            AddPeriodOption.Dates -> lastStart != null && (stillOngoing || lastEnd != null)
        }

        PrimaryCta(
            text = stringResource(Res.string.onboarding_save),
            enabled = canSave,
            onClick = {
                scope.launch {
                    val now = Clock.System.now().toEpochMilliseconds()
                    val name = if (isForMe) DEFAULT_PERSON_NAME else personName.trim().ifEmpty { DEFAULT_PERSON_NAME }
                    val savedId = if (person != null) {
                        personRepository.updatePerson(
                            person.copy(
                                name = name,
                                cycleLength = cycleLength,
                                periodLength = periodLength,
                                updatedAtEpochMillis = now,
                                isForMe = isForMe,
                            )
                        )
                        person.id
                    } else {
                        val personId = "person_${now}"
                        personRepository.insertPerson(
                            Person(
                                id = personId,
                                name = name,
                                cycleLength = cycleLength,
                                periodLength = periodLength,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now,
                                isForMe = isForMe,
                            )
                        )
                        when (periodOption) {
                            AddPeriodOption.Today ->
                                service.recordPeriodStart(personId, today)
                            AddPeriodOption.Dates -> {
                                val s = lastStart
                                if (s != null) {
                                    if (stillOngoing) service.recordPeriodStart(personId, s)
                                    else {
                                        val e = lastEnd
                                        if (e != null) service.backfillPeriod(personId, s, e)
                                        else service.recordPeriodStart(personId, s)
                                    }
                                }
                            }
                            AddPeriodOption.Unsure, null -> Unit
                        }
                        service.seedInitialHistory(personId, cycleLength, periodLength)
                        personId
                    }

                    // At most one "Me": demote any other person marked as such.
                    if (isForMe) {
                        personRepository.getPeople()
                            .filter { it.id != savedId && it.isForMe }
                            .forEach { personRepository.updatePerson(it.copy(isForMe = false)) }
                    }
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        SmallSpacer(12)
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) { Text(stringResource(Res.string.dialog_cancel)) }

        if (isEditing) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = {
                    exportStatus = ExportStatus.Idle
                    showExportDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                enabled = reportExportService != null,
            ) {
                Text(stringResource(Res.string.settings_export))
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { showDeleteConfirm = true }) {
                Text(
                    stringResource(Res.string.person_delete_title),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    when (openPicker) {
        PickerTarget.Start -> GenericDatePickerSheet(
            titleRes = Res.string.person_add_start_pick,
            minDate = earliest,
            maxDate = today,
            defaultDate = lastStart ?: today,
            onDismiss = { openPicker = null },
            onConfirm = {
                lastStart = it
                if (lastEnd != null && lastEnd!! < it) lastEnd = null
                openPicker = null
            },
        )
        PickerTarget.End -> GenericDatePickerSheet(
            titleRes = Res.string.person_add_end_pick,
            minDate = lastStart ?: earliest,
            maxDate = today,
            defaultDate = lastEnd ?: today,
            onDismiss = { openPicker = null },
            onConfirm = {
                lastEnd = it
                openPicker = null
            },
        )
        null -> Unit
    }

    if (showExportDialog && person != null) {
        ExportDialog(
            status = exportStatus,
            onExport = { language ->
                scope.launch {
                    val exporter = reportExportService ?: run {
                        exportStatus = ExportStatus.Failure("Export service not available")
                        return@launch
                    }
                    exportStatus = ExportStatus.InProgress
                    val records = service.getAllRecords(person.id)
                    exportStatus = when (val result = exporter.exportCycleReport(records, language)) {
                        is ExportResult.Success -> ExportStatus.Success(result.displayLocation)
                        is ExportResult.Failure -> ExportStatus.Failure(result.message)
                    }
                }
            },
            onDismiss = {
                showExportDialog = false
                exportStatus = ExportStatus.Idle
            },
        )
    }

    if (showDeleteConfirm && person != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.person_delete_title)) },
            text = { Text(stringResource(Res.string.person_delete_body, person.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            personRepository.deletePerson(person.id)
                            onSaved()
                        }
                    },
                ) { Text(stringResource(Res.string.detail_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            },
        )
    }
}

/**
 * Health sync row, gated by [isForMe].
 *
 * - For-Me: interactive toggle + optional "Sync now" + status message.
 * - Others: disabled card explaining sync stays local-only (single-user
 *   platform stores cannot hold several people).
 */
@Composable
private fun HealthSyncSection(
    isForMe: Boolean,
    enabled: Boolean,
    message: String?,
    onToggle: (Boolean) -> Unit,
    onSyncNow: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(Res.string.health_sync_title),
            style = MaterialTheme.typography.titleSmall,
            color = if (isForMe) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
        )
        SmallSpacer(12)
        if (isForMe) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.health_sync_toggle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        stringResource(Res.string.health_sync_subtitle_me),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled && onSyncNow != null) {
                SmallSpacer(12)
                OutlinedButton(
                    onClick = onSyncNow,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) { Text(stringResource(Res.string.health_sync_sync_now)) }
            }
            if (message != null) {
                SmallSpacer(8)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(Res.string.health_sync_disabled_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        SmallSpacer(4)
                        Text(
                            stringResource(Res.string.health_sync_disabled_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(checked = false, enabled = false, onCheckedChange = {})
                }
            }
        }
    }
}

@Composable
private fun DateRow(    label: String,
    value: String,
    hasValue: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatDate(date: LocalDate): String = "${date.month.number}/${date.day}"