package at.msd.friehs_bicha.cdcsvparser.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import at.msd.friehs_bicha.cdcsvparser.app.AppSettings
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.FirebaseAppmodel
import at.msd.friehs_bicha.cdcsvparser.core.CoreService
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * Firebase helpers.
 *
 * All Firestore work is callback based: the old versions "returned" a map
 * that could only be empty (the listener filled a local variable after the
 * function returned) and callers hid the race with Thread.sleep(...),
 * which blocked the calling thread and only sometimes worked.
 */
class FirebaseUtil(private val context: Context) {

    val userMapLiveData = MutableLiveData<HashMap<String, Any>?>()
    var userMapError = false

    /**
     * Saves the local app model to the user's Firebase document.
     * The existing document (if any) is fetched first so that unrelated
     * fields are preserved.
     *
     * @return false immediately if there is no signed-in user, otherwise
     * the save is performed asynchronously (result via toast + log).
     */
    fun saveDataToFirebase(appModel: AppModel): Boolean {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            FileLog.e("FirebaseUtil", "saveDataToFirebase: user is null")
            return false
        }
        val uid = user.uid
        val db = Firebase.firestore

        val appSettings = AppSettings(
            uid,
            PreferenceHelper.getSelectedType(context),
            PreferenceHelper.getUseStrictType(context)
        )

        getUserDataFromFirestore(uid, db) { result ->
            val existing = result.getOrNull() ?: hashMapOf<String, Any>()
            if (result.isFailure && existing.isEmpty()) {
                FileLog.i("FirebaseUtil", "No existing user document, creating a new one")
            }

            val dataMap = hashMapOf<String, Any>("appSettings" to appSettings.toHashMap())
            if (appModel.hasCard()) {
                dataMap["appModelCard"] = appModel.toHashMap(AppType.CroCard)
            }
            if (appModel.hasTxModule()) {
                dataMap["appModel"] = appModel.toHashMap()
            }
            existing.putAll(dataMap)

            db.collection("user").document(uid).set(existing)
                .addOnCompleteListener { task ->
                    handleFirebaseTaskResult(task, "Data saved successfully", "Error saving data")
                }
        }
        return true
    }

    /**
     * Loads the user's document from Firebase and starts the core service
     * with it (or shows an error for an incompatible/empty database).
     */
    fun loadDataFromFirebase() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            FileLog.e("FirebaseUtil", "loadDataFromFirebase: user is null")
            return
        }
        val db = Firebase.firestore

        getUserDataFromFirestore(user.uid, db) { result ->
            if (result.isFailure) {
                userMapError = true
                userMapLiveData.value = null
                val message = "Your database has nothing saved."
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                FileLog.e("FirebaseUtil", message)
                return@getUserDataFromFirestore
            }
            val userMap = result.getOrNull() ?: hashMapOf()
            userMapLiveData.value = userMap

            val appSettings = userMap["appSettings"] as? HashMap<String, Any>
            val dbVersion = appSettings?.get("dbVersion")?.toString()

            if (dbVersion != null && StringHelper.compareVersions(dbVersion, "1.0.0")) {
                val text =
                    "Your database is not compatible with this version of the app. Please downgrade the app or override the database with a new upload."
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                return@getUserDataFromFirestore
            }

            var hasCard = false
            var hasTxModule = false

            appSettings?.get("useStrictType")?.let {
                if (it is Boolean) {
                    PreferenceHelper.setUseStrictType(InstanceVars.applicationContext, it)
                } else {
                    FileLog.w("FirebaseUtil", "useStrictType missing or not a Boolean")
                }
            }

            (userMap["appModelCard"] as? HashMap<String, Any>)?.let { txAppMap ->
                hasCard = true
                FileLog.i("FirebaseUtil", "hasCard")
                processCardData(txAppMap, appSettings)
            }
            (userMap["appModel"] as? HashMap<String, Any>)?.let { txAppMap ->
                hasTxModule = true
                FileLog.i("FirebaseUtil", "hasTxModule")
                processTxModuleData(txAppMap, appSettings)
            }

            if (!hasCard && !hasTxModule) {
                val message = "Your database has nothing saved."
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                FileLog.e("FirebaseUtil", message)
                return@getUserDataFromFirestore
            }

            InstanceVars.applicationContext.startService(
                Intent(InstanceVars.applicationContext, CoreService::class.java)
                    .apply { action = CoreService.ACTION_START_SERVICE_WITH_FIREBASE_DATA })
        }
    }

    private fun processTxModuleData(
        txAppMap: HashMap<String, Any>,
        appSettings: HashMap<String, Any>?
    ) {
        val dbWallets =
            txAppMap["wallets"] as? java.util.ArrayList<java.util.HashMap<String, *>>
        val dbOutsideWallets =
            txAppMap["outsideWallets"] as? java.util.ArrayList<java.util.HashMap<String, *>>
        val dbTransactions =
            txAppMap["transactions"] as? java.util.ArrayList<java.util.HashMap<String, *>>
        val amountTxFailed = (txAppMap["amountTxFailed"] as? Number)?.toLong() ?: 0
        val appType = AppType.safeFromName(txAppMap["appType"] as? String ?: "")
            ?: return

        CoreService.firebaseDataLiveData.value?.add(
            FirebaseAppmodel(
                dbWallets,
                dbOutsideWallets,
                dbTransactions,
                appType,
                amountTxFailed,
                appSettings?.get("useStrictType") as? Boolean ?: true
            )
        )
    }

    private fun processCardData(
        txAppMap: HashMap<String, Any>,
        appSettings: HashMap<String, Any>?
    ) {
        val dbWallets =
            txAppMap["wallets"] as? java.util.ArrayList<java.util.HashMap<String, *>>
        val dbTransactions =
            txAppMap["transactions"] as? java.util.ArrayList<java.util.HashMap<String, *>>
        val amountTxFailed = (txAppMap["amountTxFailed"] as? Number)?.toLong() ?: 0
        val appType = AppType.safeFromName(txAppMap["appType"] as? String ?: "")
            ?: return

        CoreService.firebaseDataLiveData.value?.add(
            FirebaseAppmodel(
                dbWallets,
                null,
                dbTransactions,
                appType,
                amountTxFailed,
                appSettings?.get("useStrictType") as? Boolean ?: true
            )
        )
    }

    private fun handleFirebaseTaskResult(
        task: Task<Void>,
        successMessage: String,
        errorMessage: String
    ) {
        if (task.isSuccessful) {
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            FileLog.i("FirebaseUtil", successMessage)
        } else {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            FileLog.e("FirebaseUtil", "Error: ${task.exception}")
        }
    }

    companion object {
        /**
         * Fetches the user document. The callback receives a non-null
         * [Result] in every case: success with the document data (an empty
         * map if the document has no fields), or a failure when the
         * document does not exist / the request failed.
         */
        fun getUserDataFromFirestore(
            uid: String,
            db: FirebaseFirestore,
            callbackMethod: (Result<HashMap<String, Any>>) -> Unit
        ) {
            db.collection("user").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        callbackMethod(
                            Result.success(
                                (document.data as? HashMap<String, Any>) ?: hashMapOf()
                            )
                        )
                    } else {
                        FileLog.i("FirebaseUtil", "User document does not exist: $uid")
                        callbackMethod(
                            Result.failure(Exception("User document does not exist: $uid"))
                        )
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(
                        InstanceVars.applicationContext,
                        "Error loading data",
                        Toast.LENGTH_SHORT
                    ).show()
                    FileLog.e("FirebaseUtil", "Error getting documents: $exception")
                    callbackMethod(Result.failure(exception))
                }
        }
    }

}
