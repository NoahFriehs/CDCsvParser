package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.databinding.ItemHistoryRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * History list (2.1): pick an old upload to parse, or delete it — by swipe
 * (ItemTouchHelper) or the per-row button. Both ask for confirmation first,
 * because the files in `files/` are the only copy of the user's data on this
 * device. Selection and deletions are reported back via FragmentResult.
 */
class HistoryDialogFragment : DialogFragment() {

    private class RowHolder(val binding: ItemHistoryRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val fileList = (requireContext().filesDir.listFiles() ?: emptyArray())
            .filter { it.name.endsWith(".csv") && it.isFile }
            .sortedByDescending { it.lastModified() }

        val adapter = HistoryAdapter(fileList)
        adapter.onSelect = { file ->
            setFragmentResult(REQUESTED_SELECT, Bundle().apply { putString(KEY_FILE, file.name) })
            dismiss()
        }
        adapter.onDelete = { file -> confirmDelete(context, file, adapter) }

        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                adapter.itemAt(viewHolder.bindingAdapterPosition).let { adapter.onDelete?.invoke(it) }
            }
        }).attachToRecyclerView(recycler)

        return MaterialAlertDialogBuilder(context)
            .setTitle(R.string.history)
            .setView(recycler)
            .create()
    }

    private fun confirmDelete(context: android.content.Context, file: File, adapter: HistoryAdapter) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.history_delete_title)
            .setMessage(R.string.history_delete_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (file.delete()) {
                    adapter.remove(file.name)
                    setFragmentResult(
                        REQUESTED_DELETE, Bundle().apply { putString(KEY_FILE, file.name) }
                    )
                    // The list is empty now: nothing left to offer.
                    if (adapter.itemCount == 0) dismiss()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class HistoryAdapter(
        private var items: List<File>,
    ) : RecyclerView.Adapter<RowHolder>() {

        var onSelect: ((File) -> Unit)? = null
        var onDelete: ((File) -> Unit)? = null

        private val sdf = SimpleDateFormat(HISTORY_FILE_PATTERN, Locale.getDefault())
        private val display = SimpleDateFormat("d.M. HH:mm", Locale.getDefault())

        fun itemAt(position: Int): File = items[position]

        fun remove(fileName: String) {
            items = items.filterNot { it.name == fileName }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
            RowHolder(ItemHistoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val file = items[position]
            val raw = file.name.removeSuffix(".csv")
            holder.binding.historyRowName.text = runCatching {
                display.format(sdf.parse(raw))
            }.getOrDefault(raw)
            holder.binding.historyRowMeta.text = "${file.name} (${formatSize(file.length())})"
            holder.binding.historyRowDelete.setOnClickListener { onDelete?.invoke(file) }
            holder.binding.root.setOnClickListener { onSelect?.invoke(file) }
        }
    }

    companion object {
        const val REQUESTED_SELECT = "history_select"
        const val REQUESTED_DELETE = "history_delete"
        const val KEY_FILE = "file"

        const val HISTORY_FILE_PATTERN = "yyyy-MM-dd-HH-mm-ss"

        private fun formatSize(bytes: Long): String = when {
            bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
            bytes >= 1_000 -> "${(bytes + 500) / 1_000} kB"
            else -> "$bytes B"
        }
    }
}
