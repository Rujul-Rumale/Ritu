package com.haodong.yimalaile.ui.pages.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haodong.yimalaile.BuildKonfig
import com.haodong.yimalaile.domain.settings.AppDarkMode
import org.jetbrains.compose.resources.stringResource
import yimalaile.composeapp.generated.resources.*

@Composable
fun SettingsScreen(
    currentDarkMode: AppDarkMode,
    onDarkModeChange: (AppDarkMode) -> Unit,
    onBack: () -> Unit,
    onClearData: () -> Unit,
    onNavigateNotifications: () -> Unit = {},
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 48.dp, bottom = 24.dp),
    ) {
        // Top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(Res.string.settings_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp))
        }

        Spacer(Modifier.height(32.dp))

        // ── Display Mode — icon toggle ──
        SectionLabel(stringResource(Res.string.settings_display_mode))
        Spacer(Modifier.height(12.dp))
        InlineIconToggle(
            items = listOf(
                ToggleItem(AppDarkMode.SYSTEM, stringResource(Res.string.settings_mode_system), Icons.Outlined.Smartphone),
                ToggleItem(AppDarkMode.LIGHT, stringResource(Res.string.settings_mode_light), Icons.Outlined.LightMode),
                ToggleItem(AppDarkMode.DARK, stringResource(Res.string.settings_mode_dark), Icons.Outlined.DarkMode),
            ),
            selected = currentDarkMode,
            onSelect = onDarkModeChange,
        )

        Spacer(Modifier.height(24.dp))

        // ── Notifications ──
        SettingsItem(
            label = stringResource(Res.string.settings_notifications),
            value = stringResource(Res.string.settings_notifications_value),
            onClick = onNavigateNotifications,
        )

        Spacer(Modifier.height(24.dp))

        // ── About & version ──
        Surface(
            onClick = { showAboutSheet = true },
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.settings_about),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.app_version, BuildKonfig.VERSION_NAME),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            stringResource(Res.string.settings_made_by),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Clear data ──
        Spacer(Modifier.height(12.dp))
        SettingsItem(
            label = stringResource(Res.string.settings_clear_data),
            value = stringResource(Res.string.settings_clear_data_value),
            onClick = { showClearConfirm = true },
            destructive = true,
        )

        Spacer(Modifier.height(16.dp))
    }

    // ── Dialogs ──

    if (showClearConfirm) {
        ClearDataDialog(
            onDismiss = { showClearConfirm = false },
            onConfirm = onClearData,
        )
    }

    if (showAboutSheet) {
        AboutSheet(onDismiss = { showAboutSheet = false })
    }
}

// ════════════════════════════════════════════════════════════════
// Slider dialog for editing numeric values
// ════════════════════════════════════════════════════════════════
