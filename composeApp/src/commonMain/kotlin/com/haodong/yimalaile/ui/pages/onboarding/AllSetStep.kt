package com.haodong.yimalaile.ui.pages.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haodong.yimalaile.ui.components.PrimaryCta
import com.haodong.yimalaile.ui.components.SmallSpacer
import org.jetbrains.compose.resources.stringResource
import yimalaile.composeapp.generated.resources.Res
import yimalaile.composeapp.generated.resources.onboarding_all_set
import yimalaile.composeapp.generated.resources.onboarding_all_set_hint_simple
import yimalaile.composeapp.generated.resources.onboarding_all_set_hint_someone
import yimalaile.composeapp.generated.resources.onboarding_get_started

@Composable
internal fun AllSetStep(
    isForMe: Boolean,
    onComplete: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        SmallSpacer(24)

        Text(
            stringResource(Res.string.onboarding_all_set),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SmallSpacer(12)
        Text(
            stringResource(
                if (isForMe) Res.string.onboarding_all_set_hint_simple
                else Res.string.onboarding_all_set_hint_someone
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        PrimaryCta(
            text = stringResource(Res.string.onboarding_get_started),
            onClick = onComplete,
        )
    }
}
