package at.msd.friehs_bicha.cdcsvparser.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import at.msd.friehs_bicha.cdcsvparser.R

/**
 * About screen.
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
        return inflater.inflate(R.layout.activity_about_us, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tv_mailString).setOnClickListener {
            val emailIntent = createEmailIntent()
            emailIntent.resolveActivity(requireContext().packageManager)?.let {
                startActivity(Intent.createChooser(emailIntent, null))
            }
        }
    }

    private fun createEmailIntent(): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_ADDRESS))
        }
    }
}
