package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.ui.compose.AboutScreen
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme

/**
 * About screen (Compose surface).
 */
class AboutUsFragment : Fragment() {

    companion object {
        private const val EMAIL_ADDRESS = "cdcsvparser@gmail.com"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // This destination must never offer the settings action. The
        // gear itself comes from the view-scoped MenuProviders of the
        // other fragments; a navigation transition can briefly hold two
        // views alive at once, so this destination removes the item on
        // every menu build within its own view lifetime instead of
        // relying on the leaving fragment's provider to be gone first.
        // View-scoped: it is gone with this view.
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.findItem(R.id.action_settings)?.let { menu.removeItem(it.itemId) }
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = false
            },
            viewLifecycleOwner,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                CdcsvTheme {
                    AboutScreen(onMailClick = { sendMail() })
                }
            }
        }
    }

    private fun sendMail() {
        if (!isAdded) return
        val context = requireContext()
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_ADDRESS))
        }
        emailIntent.resolveActivity(context.packageManager)?.let {
            startActivity(Intent.createChooser(emailIntent, null))
        }
    }
}
