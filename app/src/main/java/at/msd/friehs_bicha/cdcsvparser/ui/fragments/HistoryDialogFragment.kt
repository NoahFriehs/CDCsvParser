package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.app.Dialog
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.general.HistoryFileUtil
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme
import at.msd.friehs_bicha.cdcsvparser.ui.compose.HistoryDialogContent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/**
 * History list (2.1): pick an old upload to parse, or delete it. The UI is a
 * ComposeView inside the same dialog shell, with the confirm-first delete
 * (by swipe or per-row button) kept because the files in `files/` are the
 * only copy of the user's data on this device. Selection and deletions are
 * reported back via FragmentResult.
 */
class HistoryDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val fileList = (requireContext().filesDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".csv") && it.isFile }
            .sortedByDescending { it.lastModified() }

        // The dialog reference is filled after create(); delete-to-empty
        // dismisses through it.
        var dialogRef: Dialog? = null

        // The purge set is computed HERE (not in the composable): the
        // clock stays out of the UI layer, and the candidate list is
        // fixed for the dialog's lifetime (its confirm dialog shows the
        // affected count up front). A few rows deleted in-dialog before
        // the purge are skipped harmlessly (checked below).
        val purgeCandidates = HistoryFileUtil.purgeCandidates(fileList, System.currentTimeMillis())

        val composeView = ComposeView(context).apply {
            setContent {
                CdcsvTheme {
                    HistoryDialogContent(
                        initialFiles = fileList,
                        purgeCandidates = purgeCandidates,
                        onSelect = { file ->
                            setFragmentResult(
                                REQUESTED_SELECT,
                                Bundle().apply { putString(KEY_FILE, file.name) }
                            )
                            dismiss()
                        },
                        onDeleted = {
                            setFragmentResult(
                                REQUESTED_DELETE,
                                Bundle().apply { putString(KEY_FILE, it.name) }
                            )
                        },
                        onPurge = { candidates ->
                            // The composable updates its list from the
                            // returned ACTUAL deletions; rows already
                            // removed in-dialog are no-ops, not errors.
                            val deleted = mutableListOf<File>()
                            candidates.forEach { file ->
                                if (!file.exists()) return@forEach
                                if (file.delete()) deleted.add(file)
                                else FileLog.w(TAG, "Purge: could not delete ${file.name}")
                            }
                            if (deleted.isNotEmpty()) {
                                setFragmentResult(
                                    REQUESTED_PURGE,
                                    Bundle().apply { putInt(KEY_PURGE_COUNT, deleted.size) }
                                )
                            }
                            deleted
                        },
                        onEmpty = {
                            // The list is empty now: nothing left to offer.
                            dialogRef?.dismiss()
                        },
                    )
                }
            }
        }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.history)
            .setView(composeView)
        val dialog = builder.create()
        dialogRef = dialog
        return dialog
    }

    companion object {
        private const val TAG = "HistoryDialog"
        const val REQUESTED_SELECT = "history_select"
        const val REQUESTED_DELETE = "history_delete"
        const val REQUESTED_PURGE = "history_purge"
        const val KEY_FILE = "file"
        const val KEY_PURGE_COUNT = "purge_count"
    }
}
