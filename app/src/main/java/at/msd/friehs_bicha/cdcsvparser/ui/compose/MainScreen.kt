package at.msd.friehs_bicha.cdcsvparser.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.msd.friehs_bicha.cdcsvparser.R

/**
 * Main screen (Compose, P3.1). Import a CSV (new or from history) and jump
 * into the overview. The SAF upload flow, the history dialog and the
 * progress dialog stay in the fragment.
 */
@Composable
fun MainScreen(
    isSignedIn: Boolean,
    /** Display name of the newest history file, or null when there is none. */
    newestHistoryFile: String?,
    onUploadClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLoadFromDbClick: () -> Unit,
) {
    // #47: single header bar — the settings action lives in the
    // activity's action bar (view-scoped menu provider in MainFragment),
    // like on every other screen; the old Compose gear row doubled the
    // header.
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 32.dp),
            )

            Spacer(Modifier.weight(1f))

            FilledTonalButton(
                onClick = onUploadClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_upload_24),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 8.dp),
                )
                Text(stringResource(R.string.upload_file))
            }

            AnimatedVisibility(
                visible = newestHistoryFile != null,
                enter = Motion.sectionEnter(),
                exit = Motion.sectionExit(),
            ) {
                val historyFile = newestHistoryFile.orEmpty()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.history_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onHistoryClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(historyFile)
                }
            }

            AnimatedVisibility(
                visible = isSignedIn,
                enter = Motion.sectionEnter(),
                exit = Motion.sectionExit(),
            ) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onLoadFromDbClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.load_latest_from_database))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
