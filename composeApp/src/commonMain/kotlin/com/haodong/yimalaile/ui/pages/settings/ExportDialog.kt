package com.haodong.yimalaile.ui.pages.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haodong.yimalaile.ExportStatus
import org.jetbrains.compose.resources.stringResource
import yimalaile.composeapp.generated.resources.Res
import yimalaile.composeapp.generated.resources.dialog_cancel
import yimalaile.composeapp.generated.resources.dialog_confirm
import yimalaile.composeapp.generated.resources.export_button
import yimalaile.composeapp.generated.resources.export_dialog_body
import yimalaile.composeapp.generated.resources.export_dialog_title
import yimalaile.composeapp.generated.resources.export_failure_body
import yimalaile.composeapp.generated.resources.export_failure_title
import yimalaile.composeapp.generated.resources.export_in_progress
import yimalaile.composeapp.generated.resources.export_success_body
import yimalaile.composeapp.generated.resources.export_success_title

/**
 * Dialog that triggers the PDF export.
 * Also surfaces in-progress / success / failure states from [ExportStatus]
 * in place, so the user never has to chase a toast.
 */
@Composable
internal fun ExportDialog(
    status: ExportStatus,
    onExport: (language: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = status is ExportStatus.InProgress

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            val title = when (status) {
                is ExportStatus.Success -> stringResource(Res.string.export_success_title)
                is ExportStatus.Failure -> stringResource(Res.string.export_failure_title)
                else -> stringResource(Res.string.export_dialog_title)
            }
            Text(title)
        },
        text = {
            when (status) {
                is ExportStatus.Success -> {
                    Text(stringResource(Res.string.export_success_body))
                }
                is ExportStatus.Failure -> {
                    Text(stringResource(Res.string.export_failure_body, status.message))
                }
                is ExportStatus.InProgress -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(Res.string.export_in_progress))
                    }
                }
                ExportStatus.Idle -> {
                    Text(stringResource(Res.string.export_dialog_body))
                }
            }
        },
        confirmButton = {
            when (status) {
                ExportStatus.Idle -> {
                    TextButton(onClick = { onExport("en") }) {
                        Text(stringResource(Res.string.export_button))
                    }
                }
                is ExportStatus.Success, is ExportStatus.Failure -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.dialog_confirm))
                    }
                }
                is ExportStatus.InProgress -> {
                    // No action while busy.
                }
            }
        },
        dismissButton = if (status is ExportStatus.Idle) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        } else null,
    )
}
