package com.haodong.yimalaile.ui.pages.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haodong.yimalaile.ui.components.PrimaryCta
import com.haodong.yimalaile.ui.components.SmallSpacer
import org.jetbrains.compose.resources.stringResource
import yimalaile.composeapp.generated.resources.Res
import yimalaile.composeapp.generated.resources.onboarding_back
import yimalaile.composeapp.generated.resources.onboarding_continue
import yimalaile.composeapp.generated.resources.onboarding_purpose_hint
import yimalaile.composeapp.generated.resources.onboarding_purpose_me_subtitle
import yimalaile.composeapp.generated.resources.onboarding_purpose_me_title
import yimalaile.composeapp.generated.resources.onboarding_purpose_someone_subtitle
import yimalaile.composeapp.generated.resources.onboarding_purpose_someone_title
import yimalaile.composeapp.generated.resources.onboarding_purpose_title

/**
 * First onboarding question: is this phone being set up for the owner
 * themselves ("For me") or to track someone else? The answer drives
 * first-vs-third-person copy for the rest of onboarding.
 */
@Composable
internal fun PurposeStep(
    selected: Boolean?,
    onSelect: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(Res.string.onboarding_purpose_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SmallSpacer(8)
        Text(
            stringResource(Res.string.onboarding_purpose_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        SmallSpacer(40)

        PurposeCard(
            icon = Icons.Outlined.Face,
            accent = MaterialTheme.colorScheme.primary,
            title = stringResource(Res.string.onboarding_purpose_me_title),
            subtitle = stringResource(Res.string.onboarding_purpose_me_subtitle),
            selected = selected == true,
            onClick = { onSelect(true) },
        )
        SmallSpacer(16)
        PurposeCard(
            icon = Icons.Outlined.People,
            accent = MaterialTheme.colorScheme.tertiary,
            title = stringResource(Res.string.onboarding_purpose_someone_title),
            subtitle = stringResource(Res.string.onboarding_purpose_someone_subtitle),
            selected = selected == false,
            onClick = { onSelect(false) },
        )

        Spacer(Modifier.weight(1f))

        PrimaryCta(
            text = stringResource(Res.string.onboarding_continue),
            onClick = onContinue,
            enabled = selected != null,
        )
        SmallSpacer(4)
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.onboarding_back)) }
        }
    }
}

@Composable
private fun PurposeCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val border = if (selected) BorderStroke(2.dp, accent.copy(alpha = 0.75f)) else null

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = container,
        border = border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SmallSpacer(2)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}