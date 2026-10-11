package com.haodong.yimalaile.ui.pages.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.haodong.yimalaile.domain.menstrual.CyclePhase
import com.haodong.yimalaile.domain.menstrual.CyclePhaseInfo
import com.haodong.yimalaile.domain.menstrual.MenstrualService
import com.haodong.yimalaile.domain.person.Person
import com.haodong.yimalaile.ui.components.DecorShape
import com.haodong.yimalaile.ui.components.PrimaryCta
import com.haodong.yimalaile.ui.components.SmallSpacer
import com.haodong.yimalaile.ui.theme.expressiveShapes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import yimalaile.composeapp.generated.resources.Res
import yimalaile.composeapp.generated.resources.people_add
import yimalaile.composeapp.generated.resources.people_cycle_days
import yimalaile.composeapp.generated.resources.people_empty_hint
import yimalaile.composeapp.generated.resources.people_empty_title
import yimalaile.composeapp.generated.resources.people_me_badge
import yimalaile.composeapp.generated.resources.people_period_days
import yimalaile.composeapp.generated.resources.people_phase_line
import yimalaile.composeapp.generated.resources.people_title
import yimalaile.composeapp.generated.resources.phase_follicular
import yimalaile.composeapp.generated.resources.phase_luteal
import yimalaile.composeapp.generated.resources.phase_menstrual
import yimalaile.composeapp.generated.resources.phase_ovulation
import kotlin.time.Clock

/**
 * Dashboard of every tracked person. Flat list — there is no "primary"
 * person; each person is equal and gets a phase-aware avatar.
 */
@Composable
fun PeopleScreen(
    people: List<Person>,
    service: MenstrualService,
    onPersonClick: (Person) -> Unit,
    onAddPerson: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp))
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(Res.string.people_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings, modifier = Modifier.testTag("nav_settings")) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (people.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DecorShape(
                    56,
                    shape = MaterialTheme.expressiveShapes.cookie9,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                )
                SmallSpacer(24)
                Text(
                    stringResource(Res.string.people_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                SmallSpacer(8)
                Text(
                    stringResource(Res.string.people_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                people.sortedBy { it.createdAtEpochMillis }.forEach { person ->
                    PersonCard(
                        person = person,
                        service = service,
                        onClick = { onPersonClick(person) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryCta(
            text = stringResource(Res.string.people_add),
            onClick = onAddPerson,
        )
    }
}

@Composable
private fun PersonCard(
    person: Person,
    service: MenstrualService,
    onClick: () -> Unit,
) {
    val phaseInfo by produceState<CyclePhaseInfo?>(
        initialValue = null,
        key1 = person.id,
        key2 = person.cycleLength,
    ) {
        val state = service.getCycleState(person.id, person.cycleLength)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        value = CyclePhaseInfo.getPhaseInfo(today, state, person.cycleLength)
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(
            person = person,
            phaseInfo = phaseInfo,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    person.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (person.isForMe) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            stringResource(Res.string.people_me_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            SmallSpacer(4)
            phaseInfo?.let { info ->
                val phaseName = when (info.phase) {
                    CyclePhase.MENSTRUAL -> stringResource(Res.string.phase_menstrual)
                    CyclePhase.FOLLICULAR -> stringResource(Res.string.phase_follicular)
                    CyclePhase.OVULATION -> stringResource(Res.string.phase_ovulation)
                    CyclePhase.LUTEAL -> stringResource(Res.string.phase_luteal)
                }
                Text(
                    stringResource(Res.string.people_phase_line, phaseName, info.dayInCycle),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SmallSpacer(2)
            }
            Text(
                "${stringResource(Res.string.people_cycle_days, person.cycleLength)} · " +
                    stringResource(Res.string.people_period_days, person.periodLength),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Round avatar tinted by the person's current phase, with a phase symbol. */
@Composable
private fun PersonAvatar(
    person: Person,
    phaseInfo: CyclePhaseInfo?,
) {
    val phaseIcon: ImageVector? = when (phaseInfo?.phase) {
        CyclePhase.MENSTRUAL -> Icons.Outlined.WaterDrop
        CyclePhase.FOLLICULAR -> Icons.Outlined.Eco
        CyclePhase.OVULATION -> Icons.Outlined.WbSunny
        CyclePhase.LUTEAL -> Icons.Outlined.Bedtime
        null -> null
    }
    val (bg, fg) = when (phaseInfo?.phase) {
        CyclePhase.MENSTRUAL -> MaterialTheme.colorScheme.error to Color.White
        CyclePhase.FOLLICULAR -> MaterialTheme.colorScheme.primary to Color.White
        CyclePhase.OVULATION -> MaterialTheme.colorScheme.tertiary to Color.White
        CyclePhase.LUTEAL -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
        null -> personAvatarColor(person, MaterialTheme.colorScheme.primary) to Color.White
    }

    Box(
        Modifier.size(52.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            phaseIcon ?: Icons.Outlined.Person,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Deterministic avatar color: colorIndex takes precedence, else a stable name/id hash. */
@Composable
internal fun personAvatarColor(person: Person, fallback: Color): Color {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
        MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
    )
    val index = if (person.colorIndex != 0) person.colorIndex else person.id.hashCode()
    val safe = (index and 0x7fffffff).toLong()
    return if (palette.isEmpty()) fallback else palette[((safe * 31) % palette.size).toInt()]
}