package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.MainFragment
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Upload history list (Compose, P3.1): pick an old upload to parse or delete
 * it — by swipe or the per-row button. Both ask for confirmation first,
 * because the files in `files/` are the only copy of the user's data on this
 * device. Lives in a ComposeView inside the dialog shell; results are
 * reported back via FragmentResult by the hosting fragment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDialogContent(
    initialFiles: List<java.io.File>,
    onSelect: (java.io.File) -> Unit,
    /** Called after a confirmed deletion (file already removed from disk). */
    onDeleted: (java.io.File) -> Unit,
    /** Called when the last file was deleted, so the shell can dismiss. */
    onEmpty: () -> Unit,
) {
    var files by remember { mutableStateOf(initialFiles) }
    var fileToConfirmDelete by remember { mutableStateOf<java.io.File?>(null) }

    val sdf = remember { SimpleDateFormat(MainFragment.HISTORY_FILE_PATTERN, Locale.getDefault()) }
    val display = remember { SimpleDateFormat("d.M. HH:mm", Locale.getDefault()) }

    val confirmFile = fileToConfirmDelete
    if (confirmFile != null) {
        AlertDialog(
            onDismissRequest = { fileToConfirmDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToConfirmDelete = null
                        if (confirmFile.delete()) {
                            files = files.filterNot { it.name == confirmFile.name }
                            onDeleted(confirmFile)
                            if (files.isEmpty()) onEmpty()
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { fileToConfirmDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    // MOD 2.6: rows stagger in during the initial settle only.
    val settleStartedAt = rememberListSettleStart()
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(files) { index, file ->
            val state = rememberSwipeToDismissBoxState(
                initialValue = SwipeToDismissBoxValue.Settled,
                confirmValueChange = { it != SwipeToDismissBoxValue.Settled },
            )
            StaggeredListAppear(index, settleStartedAt) {
                SwipeToDismissBox(
                    state = state,
                    enableDismissFromStartToEnd = true,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        BoxCenter(
                            background = MaterialTheme.colorScheme.errorContainer,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onSelect(file) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                        ) {
                            Text(
                                text = displayFor(file.name, sdf, display),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${file.name} (${formatSize(file.length())})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { fileToConfirmDelete = file }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxCenter(background: androidx.compose.ui.graphics.Color, tint: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun displayFor(fileName: String, sdf: SimpleDateFormat, display: SimpleDateFormat): String {
    val raw = fileName.removeSuffix(".csv")
    return runCatching { display.format(sdf.parse(raw)) }.getOrDefault(raw)
}

/** Same size formatting as the old dialog. */
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> "${(bytes + 500) / 1_000} kB"
    else -> "$bytes B"
}
