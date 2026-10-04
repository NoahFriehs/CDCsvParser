package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import at.msd.friehs_bicha.cdcsvparser.ui.compose.AboutScreen
import at.msd.friehs_bicha.cdcsvparser.ui.compose.CdcsvTheme

/**
 * About screen (Compose surface, P3.1).
 */
class AboutUsFragment : Fragment() {

    companion object {
        private const val EMAIL_ADDRESS = "cdcsvparser@gmail.com"
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
