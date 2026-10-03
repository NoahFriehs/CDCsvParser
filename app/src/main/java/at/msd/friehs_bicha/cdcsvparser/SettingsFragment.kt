package at.msd.friehs_bicha.cdcsvparser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.databinding.ActivitySettingsBinding
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.util.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.appcompat.app.AlertDialog

class SettingsFragment : Fragment() {

    private var _binding: ActivitySettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivitySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var useStrictType = false
        var selectedType: AppType = AppType.CdCsvParser
        var useCpp = true

        val appTypeSpinner: Spinner = binding.walletTypeSpinner
        val coreModeSpinner: Spinner = binding.coreModeSpinner
        val useStrictTypeCheckbox: CheckBox = binding.useStrictWalletTypeCheckbox
        val cbStoreDataLocal: CheckBox = binding.root.findViewById(R.id.cb_store_data_local)
        val cbEnableFastStart: CheckBox = binding.root.findViewById(R.id.cb_enable_fast_start)
        val btnAboutUs: Button = binding.root.findViewById(R.id.btn_about_us)
        val btnLogout: Button = binding.root.findViewById(R.id.btn_logout)
        val btnLogin: Button = binding.root.findViewById(R.id.btn_login)
        val btnDeleteUser: Button = binding.root.findViewById(R.id.btn_delete_account)

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        selectedType = PreferenceHelper.getSelectedType(requireContext())
        useStrictType = PreferenceHelper.getUseStrictType(requireContext())

        appTypeSpinner.setSelection(selectedType.ordinal)
        coreModeSpinner.setSelection(0)
        if (!PreferenceHelper.getUseCpp(requireContext())) {
            coreModeSpinner.setSelection(1)
        }
        useStrictTypeCheckbox.isEnabled = selectedType != AppType.CroCard
        useStrictTypeCheckbox.isChecked = useStrictType
        useStrictTypeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            useStrictType = isChecked
            PreferenceHelper.setUseStrictType(requireContext(), isChecked)
        }

        cbStoreDataLocal.isChecked = PreferenceHelper.getIsDataLocal(requireContext())
        cbStoreDataLocal.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.setIsDataLocal(requireContext(), isChecked)
            cbEnableFastStart.isEnabled = cbStoreDataLocal.isEnabled && cbStoreDataLocal.isChecked
        }

        cbEnableFastStart.isChecked = PreferenceHelper.getFastStartEnabled(requireContext())
        cbEnableFastStart.isEnabled = cbStoreDataLocal.isEnabled && cbStoreDataLocal.isChecked
        cbEnableFastStart.setOnCheckedChangeListener { _, isChecked ->
            PreferenceHelper.setFastStartEnabled(requireContext(), isChecked)
        }

        appTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View, position: Int, id: Long
            ) {
                selectedType = AppType.values()[position]
                PreferenceHelper.setSelectedType(requireContext(), selectedType)
                useStrictTypeCheckbox.isEnabled = position != 0
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        coreModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View, position: Int, id: Long
            ) {
                useCpp = position == 0
                PreferenceHelper.setUseCpp(requireContext(), useCpp)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnAboutUs.setOnClickListener {
            findNavController().navigate(R.id.aboutUsFragment)
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.logout))
                .setMessage(getString(R.string.logoutQuestion))
                .setPositiveButton(getString(R.string.yes)) { _, _ -> logout(auth) }
                .setNegativeButton(getString(R.string.no), null)
                .create()
                .show()
        }

        btnLogin.setOnClickListener {
            findNavController().navigate(R.id.loginFragment)
        }

        btnDeleteUser.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.delete_account))
                .setMessage(getString(R.string.deleteQuestion))
                .setPositiveButton(getString(R.string.yes)) { _, _ -> deleteUser(user) }
                .setNegativeButton(getString(R.string.no), null)
                .create()
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null) return

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            _binding!!.root.findViewById<Button>(R.id.btn_logout).visibility = View.GONE
            _binding!!.root.findViewById<Button>(R.id.btn_delete_account).visibility = View.GONE
            _binding!!.root.findViewById<Button>(R.id.btn_login).visibility = View.VISIBLE
        } else {
            _binding!!.root.findViewById<Button>(R.id.btn_logout).visibility = View.VISIBLE
            _binding!!.root.findViewById<Button>(R.id.btn_delete_account).visibility = View.VISIBLE
            _binding!!.root.findViewById<Button>(R.id.btn_login).visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun deleteUser(user: FirebaseUser?) {
        if (user == null) {
            FileLog.e("Settings-DeleteUser", "deleteUser: user is null")
            return
        }
        val db = Firebase.firestore
        // Delete the data document first and do NOT write a placeholder before
        // it: if the delete fails (offline, rules, quota) the user's document
        // must stay untouched instead of being wiped by the placeholder write.
        db.collection("user").document(user.uid).delete().addOnCompleteListener {
            if (it.isSuccessful) {
                FileLog.d("Settings-DeleteUser", "User deleted from database.")
            } else {
                FileLog.e("Settings-DeleteUser", "User could not be deleted from database: ${it.exception}")
            }
        }

        user.delete().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                FileLog.d("Settings-DeleteUser", "User account deleted.")
                Toast.makeText(requireContext(), getString(R.string.user_deleted), Toast.LENGTH_LONG)
                    .show()
            } else {
                val exception = task.exception
                if (exception != null) {
                    FileLog.d("Settings-DeleteUser", exception.toString())
                }
                if (exception?.toString()?.contains("requires recent authentication") == true) {
                    FileLog.d("Settings-DeleteUser", "User needs to reauthenticate.")
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.user_needs_to_autheticate),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        logout(FirebaseAuth.getInstance())
    }

    private fun logout(auth: FirebaseAuth) {
        if (!isAdded) return
        auth.signOut()
        findNavController().popBackStack(R.id.mainFragment, false)
    }
}
