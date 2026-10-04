package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.app.Dialog
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import at.msd.friehs_bicha.cdcsvparser.R
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

        val composeView = ComposeView(context).apply {
            setContent {
                CdcsvTheme {
                    HistoryDialogContent(
                        initialFiles = fileList,
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
        const val REQUESTED_SELECT = "history_select"
        const val REQUESTED_DELETE = "history_delete"
        const val KEY_FILE = "file"
    }
}
