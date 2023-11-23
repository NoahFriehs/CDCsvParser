package at.msd.friehs_bicha.cdcsvparser.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import at.msd.friehs_bicha.cdcsvparser.Core.CoreService
import at.msd.friehs_bicha.cdcsvparser.app.AppSettings
import at.msd.friehs_bicha.cdcsvparser.app.AppType
import at.msd.friehs_bicha.cdcsvparser.app.FirebaseAppmodel
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.instance.InstanceVars
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class FirebaseUtil(private val context: Context) {

    val userMapLiveData = MutableLiveData<HashMap<String, Any>>()
    var userMapError = false

    fun saveDataToFirebase(appModel: AppModel) {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val db = Firebase.firestore

        var userMap = getUserDataFromFirestore(
            uid,
            db
        ) //it's a live data, so we have to wait for it to be set
        Thread.sleep(300)
        userMap = userMapLiveData.value
        if (userMap == null || userMap.isEmpty()) {
            FileLog.i("FirebaseUtil", "userMap is null or empty when saving")
            userMap = hashMapOf<String, Any>()
        }

        val appSettings = AppSettings(
            uid,
            PreferenceHelper.getSelectedType(context),
            PreferenceHelper.getUseStrictType(context)
        )
        val appSettingsMap = appSettings.toHashMap()

        userMap.let {
            val dataMap = hashMapOf("appSettings" to appSettingsMap)

            if (appModel.hasCard()) {
                dataMap["appModelCard"] = appModel.toHashMap(AppType.CroCard)
            }
            if (appModel.hasTxModule()) {
                dataMap["appModel"] = appModel.toHashMap()
            }

            it.putAll(dataMap)

            db.collection("user").document(uid).set(it)
                .addOnCompleteListener { task ->
                    handleFirebaseTaskResult(task, "Data saved successfully", "Error saving data")
                }
        }
    }

    fun loadDataFromFirebase() {
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val db = Firebase.firestore

        var userMap = getUserDataFromFirestore(
            uid,
            db
        ) //it's a live data, so we have to wait for it to be set
        Thread.sleep(1000)
        userMap = userMapLiveData.value

        userMap?.let {
            val appSettings = it["appSettings"] as HashMap<String, Any>?
            val dbVersion = appSettings?.get("dbVersion")

            if (StringHelper.compareVersions(dbVersion as String, "1.0.0")) {
                val text =
                    "Your database is not compatible with this version of the app. Please downgrade the app or override the database with a new upload."
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                return
            }

            var hasCard = false
            var hasTxModule = false

            PreferenceHelper.setUseStrictType(
                InstanceVars.applicationContext,
                appSettings["useStrictType"] as Boolean
            )

            if (it.containsKey("appModelCard")) {
                hasCard = true
                FileLog.i("FirebaseUtil", "hasCard")
                processCardData(it["appModelCard"] as HashMap<String, Any>, appSettings)
            }
            if (it.containsKey("appModel")) {
                hasTxModule = true
                FileLog.i("FirebaseUtil", "hasTxModule")
                processTxModuleData(it["appModel"] as HashMap<String, Any>, appSettings)
            }

            if (!hasCard && !hasTxModule) {
                val text =
                    "Your database has nothing saved."
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                FileLog.e("FirebaseUtil", text)
                return
            }

            InstanceVars.applicationContext.startService(
                Intent(
                    InstanceVars.applicationContext,
                    CoreService::class.java
                ).apply {
                    action = CoreService.ACTION_START_SERVICE_WITH_FIREBASE_DATA
                })


        }

    }

    private fun processTxModuleData(
        txAppMap: HashMap<String, Any>,
        appSettings: HashMap<String, Any>
    ) {
        val dbWallets = txAppMap["wallets"]
        val dbOutsideWallets =
            txAppMap["outsideWallets"] as java.util.ArrayList<java.util.HashMap<String, *>>?
        val dbTransactions =
            txAppMap["transactions"]
        val amountTxFailed = txAppMap["amountTxFailed"] as Long? ?: 0
        val appTypeString = txAppMap["appType"] as String? ?: ""
        val appType = AppType.valueOf(appTypeString)

        CoreService.firebaseDataLiveData.value?.add(
            FirebaseAppmodel(
                dbWallets as java.util.ArrayList<java.util.HashMap<String, *>>?,
                dbOutsideWallets,
                dbTransactions as java.util.ArrayList<java.util.HashMap<String, *>>?,
                appType,
                amountTxFailed,
                appSettings["useStrictType"] as Boolean
            )
        )
    }

    private fun processCardData(txAppMap: HashMap<String, Any>, appSettings: HashMap<String, Any>) {
        val dbOutsideWallets: ArrayList<java.util.HashMap<String, *>>? = null
        val dbWallets = txAppMap["wallets"]
        val dbTransactions =
            txAppMap["transactions"]
        val amountTxFailed = txAppMap["amountTxFailed"] as Long? ?: 0
        val appTypeString = txAppMap["appType"] as String? ?: ""
        val appType = AppType.valueOf(appTypeString)

        CoreService.firebaseDataLiveData.value?.add(
            FirebaseAppmodel(
                dbWallets as ArrayList<java.util.HashMap<String, *>>?,
                dbOutsideWallets,
                dbTransactions as ArrayList<java.util.HashMap<String, *>>?,
                appType,
                amountTxFailed,
                appSettings["useStrictType"] as Boolean
            )
        )
    }

    @Suppress("UN")
    private fun getUserDataFromFirestore(
        uid: String,
        db: FirebaseFirestore
    ): HashMap<String, Any>? {
        val user = db.collection("user").document(uid)
        user.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val userMap = document.data as HashMap<String, Any>?
                    if (userMap == null) {
                        Toast.makeText(context, "Error loading data", Toast.LENGTH_SHORT).show()
                        userMapError = true
                        return@addOnSuccessListener
                    }
                    userMapLiveData.value = userMap!!   //go f yourself kotlin  :(
                } else {
                    Toast.makeText(context, "Error loading data", Toast.LENGTH_SHORT).show()
                    userMapError = true
                    return@addOnSuccessListener
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(context, "Error loading data", Toast.LENGTH_SHORT).show()
                userMapError = true
                return@addOnFailureListener
            }

        return userMapLiveData.value
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

}
