package at.msd.friehs_bicha.cdcsvparser.ui.activity.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.app.AppModelManager
import at.msd.friehs_bicha.cdcsvparser.databinding.FragmentOverviewBinding
import at.msd.friehs_bicha.cdcsvparser.ui.fragments.WalletListFragment

/**
 * A placeholder fragment containing a simple view.
 */
class PlaceholderFragment : Fragment() {

    private lateinit var pageViewModel: PageViewModel
    private var _binding: FragmentOverviewBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(this)[PageViewModel::class.java].apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
        val root = binding.root

        /*pageViewModel.text.observe(viewLifecycleOwner, Observer {

        })*/

        // Check if the fragment is already added to avoid overlapping
        if (childFragmentManager.findFragmentById(R.id.fragment_container) == null) {

            val wallets =
                when (arguments?.getInt(ARG_SECTION_NUMBER)) {
                    1 -> AppModelManager.getInstance()!!.txApp!!.wallets
                    2 -> AppModelManager.getInstance()!!.txApp!!.wallets.subList(0, 2)    //get CARD wallets -> TODO save CARD wallets in AppModel -> store Card and Crypto simultaneously
                    else -> throw Exception() //AppModelManager.getInstance()!!.txApp!!.wallets
                }

            val fragment = WalletListFragment(wallets)

            childFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }


        return root
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): PlaceholderFragment {
            return PlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}