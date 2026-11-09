package at.msd.friehs_bicha.cdcsvparser.util

import at.msd.friehs_bicha.cdcsvparser.App

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import at.msd.friehs_bicha.cdcsvparser.app.AppSettings
import at.msd.friehs_bicha.cdcsvparser.general.AppModel
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import at.msd.friehs_bicha.cdcsvparser.transactions.CCDBTransaction
import at.msd.friehs_bicha.cdcsvparser.transactions.DBTransaction
import at.msd.friehs_bicha.cdcsvparser.wallet.CCDBWallet
import at.msd.friehs_bicha.cdcsvparser.wallet.DBWallet
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import javax.crypto.SecretKey

/**
 * Firebase helpers.
 *
 * All Firestore work is callback based: the old versions "returned" a map
 * that could only be empty (the listener filled a local variable after the
 * function returned) and callers hid the race with Thread.sleep(...),
 * which blocked the calling thread and only sometimes worked.
 */
class FirebaseUtil(private val context: Context) {

    /**
     * Saves the local app model to the user's Firebase document.
     *
     * This is the Kotlin-model fallback writer (the C++ core is not
     * initialized): it stores the SAME canonical encrypted document the
     * core path writes, so the document has one shape regardless of
     * which writer produced it. The payload (the wallet/transaction
     * collections) is AES-256-GCM ciphertext; `appSettings` (incl.
     * `dbVersion`, the pre-decrypt compatibility gate) stays in the
     * clear. The Kotlin model carries the crypto half only - the card
     * path is core-owned, so its collections are written empty.
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
        val txApp = appModel.txApp
        if (txApp == null) {
            FileLog.e("FirebaseUtil", "saveDataToFirebase: app model has no tx module")
            return false
        }
        val uid = user.uid
        val db = Firebase.firestore

        val appSettings = AppSettings(
            uid,
            PreferenceHelper.getSelectedType(context),
            PreferenceHelper.getUseStrictType(context)
        )
        // The restore path decides from these flags: derive them from
        // what is actually in the model.
        if (!txApp.wallets.isNullOrEmpty() ||
            !txApp.outsideWallets.isNullOrEmpty() ||
            !txApp.transactions.isNullOrEmpty()
        ) {
            appSettings.hasCryptoTx = "true"
        }

        // KDF/GCM over the whole export must not run on the caller: IO pool.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dbWallets = ArrayList<DBWallet>()
                val dbOutsideWallets = ArrayList<DBWallet>()
                val dbTransactions = ArrayList<DBTransaction>()
                txApp.wallets.forEach { dbWallets.add(DBWallet(it)) }
                txApp.outsideWallets.forEach { dbOutsideWallets.add(DBWallet(it)) }
                txApp.transactions.forEach { dbTransactions.add(DBTransaction(it)) }

                val collections = buildCollectionsMap(
                    dbWallets,
                    dbOutsideWallets,
                    dbTransactions,
                    emptyList(),
                    emptyList(),
                )
                val docFields = buildEncryptedUserDoc(appSettings.toHashMap(), collections)
                writeUserDoc(
                    uid,
                    db,
                    docFields,
                    context,
                    context.getString(R.string.data_saved_successfully),
                    context.getString(R.string.error_saving_data),
                )
            } catch (e: Exception) {
                FileLog.e("FirebaseUtil", "saveDataToFirebase: ${e.message}")
                Toast.makeText(
                    context, context.getString(R.string.error_saving_data), Toast.LENGTH_SHORT
                ).show()
            }
        }
        return true
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
                        App.instance,
                        App.instance.getString(R.string.error_loading_data),
                        Toast.LENGTH_SHORT
                    ).show()
                    FileLog.e("FirebaseUtil", "Error getting documents: $exception")
                    callbackMethod(Result.failure(exception))
                }
        }

        /**
         * Normalizes a fetched document for the cipher layer: the SDK
         * returns byte fields as [Blob], the cipher reads plain byte
         * arrays. One level is the whole surface - the cipher fields sit
         * top level, and no other field of this document is a byte field.
         */
        fun toPlainByteDoc(userMap: Map<String, Any>): HashMap<String, Any> {
            val out = HashMap<String, Any>(userMap.size)
            for ((field, value) in userMap) {
                out[field] = (value as? Blob)?.toBytes() ?: value
            }
            return out
        }

        /**
         * Resolves the local key for a document's key metadata from
         * device + settings: the device-bound keystore key, or the key
         * derived from the stored cloud password. null = the device has
         * no key for this document (the explicit "cannot read" state,
         * the documented fallback is wipe + re-import).
         */
        fun keyForDocMetadata(
            keyModel: String,
            kdfSalt: ByteArray?,
            kdfIterations: Int?,
        ): SecretKey? = when (keyModel) {
            CoreCipher.KEY_MODEL_KEYSTORE -> CoreCipher.getOrCreateKeystoreKey()

            CoreCipher.KEY_MODEL_PASSWORD -> {
                val password = PreferenceHelper.getCloudPassword(App.instance)
                if (password.isEmpty() || kdfSalt == null || kdfIterations == null) {
                    null
                } else {
                    CoreCipher.derivePasswordKey(password, kdfSalt, kdfIterations)
                }
            }

            else -> null
        }

        /**
         * Assembles the full user document for an encrypted upload:
         * `appSettings` (the compatibility gate, in the clear by design)
         * + the payload ciphertext + the cipher metadata (version, key
         * model, a fresh random salt + the iteration count for the
         * password model). The key model comes from the settings
         * preference.
         *
         * @throws IllegalStateException when the device has no key for
         * the selected model (e.g. empty password while in password
         * mode).
         */
        fun buildEncryptedUserDoc(
            appSettings: HashMap<String, Any>,
            payload: Map<String, Any?>,
        ): HashMap<String, Any> {
            val keyModel = PreferenceHelper.getCloudKeyModel(App.instance)
            val kdfSalt: ByteArray? =
                if (keyModel == CoreCipher.KEY_MODEL_PASSWORD) CoreCipher.randomKdfSalt() else null
            val kdfIterations: Int? =
                if (kdfSalt != null) CoreCipher.KDF_ITERATIONS else null
            val key = keyForDocMetadata(keyModel, kdfSalt, kdfIterations)
                ?: throw IllegalStateException("no local encryption key for model: $keyModel")
            val doc = HashMap<String, Any>()
            doc["appSettings"] = appSettings
            doc.putAll(
                CoreCipher.buildEncryptedDocFields(payload, key, keyModel, kdfSalt, kdfIterations)
            )
            return doc
        }

        /**
         * Wraps the top-level byte arrays for the SDK write path (the
         * cipher fields: payload + KDF salt); everything else passes
         * through unchanged.
         */
        fun toSdkByteDoc(docFields: HashMap<String, Any>): HashMap<String, Any> {
            val out = HashMap<String, Any>(docFields.size)
            for ((field, value) in docFields) {
                out[field] = (value as? ByteArray)?.let { Blob.fromBytes(it) } ?: value
            }
            return out
        }

        /**
         * Writes the user document with a single `set` (no field merge: an
         * encrypted payload makes partial documents meaningless) and
         * reports the result via toast + log. The SDK's byte type is
         * [Blob]: raw `byte[]` values are rejected by `set` ("Arrays are
         * not supported"), so the cipher fields (payload + KDF salt)
         * cross the SDK boundary wrapped.
         */
        fun writeUserDoc(
            uid: String,
            db: FirebaseFirestore,
            docFields: HashMap<String, Any>,
            context: Context,
            successMessage: String,
            errorMessage: String,
        ): Task<Void> {
            val toWrite = toSdkByteDoc(docFields)
            val task = db.collection("user").document(uid).set(toWrite)
            task.addOnCompleteListener { completed ->
                if (completed.isSuccessful) {
                    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                    FileLog.i("FirebaseUtil", successMessage)
                } else {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                    FileLog.e("FirebaseUtil", "Error: ${completed.exception}")
                }
            }
            return task
        }
    }
}

// ----------------------------------------------------------------------
// Document-shape helpers (shared by the two writers)
// ----------------------------------------------------------------------

/**
 * The canonical payload of the user document: the five collections in
 * firestore-shaped form (primitive values, the date as the Firestore
 * date object) - exactly the top-level maps the restore path reads
 * back after a document round trip.
 */
internal fun buildCollectionsMap(
    wallets: List<DBWallet>,
    outsideWallets: List<DBWallet>,
    transactions: List<DBTransaction>,
    cardWallets: List<CCDBWallet>,
    cardTransactions: List<CCDBTransaction>,
): HashMap<String, Any> = HashMap<String, Any>().apply {
    this["wallets"] = wallets.mapTo(java.util.ArrayList()) { it.toFirestoreMap() }
    this["outsideWallets"] = outsideWallets.mapTo(java.util.ArrayList()) { it.toFirestoreMap() }
    this["transactions"] = transactions.mapTo(java.util.ArrayList()) { it.toFirestoreMap() }
    this["cardWallets"] = cardWallets.mapTo(java.util.ArrayList()) { it.toFirestoreMap() }
    this["cardTransactions"] = cardTransactions.mapTo(java.util.ArrayList()) { it.toFirestoreMap() }
}

/**
 * The firestore-shaped entry map of a transaction. Null values are
 * OMITTED - a Firestore field null and an absent field read back
 * identically (the restore readers treat a missing key as null). Card
 * rows get the merchant string key.
 */
internal fun DBTransaction.toFirestoreMap(): HashMap<String, Any> =
    HashMap<String, Any>().apply {
        this["transactionId"] = transactionId
        this["description"] = description
        this["walletId"] = walletId
        this["fromWalletId"] = fromWalletId
        date?.let { this["date"] = dateToTimestamp(it) }
        this["currencyType"] = currencyType
        this["amount"] = amount
        this["nativeAmount"] = nativeAmount
        amountBonus?.let { this["amountBonus"] = it }
        transactionType?.let { this["transactionType"] = it }
        transHash?.let { this["transHash"] = it }
        toCurrency?.let { this["toCurrency"] = it }
        toAmount?.let { this["toAmount"] = it }
        this["outsideTransaction"] = isOutsideTransaction
        if (this@toFirestoreMap is CCDBTransaction) {
            transactionTypeString?.let { this["transactionTypeString"] = it }
        }
    }

/**
 * The firestore-shaped entry map of a wallet (an absent nested list
 * reads back as "no transactions", like a null one). Card wallets get
 * the type key.
 */
internal fun DBWallet.toFirestoreMap(): HashMap<String, Any> =
    HashMap<String, Any>().apply {
        this["walletId"] = walletId
        this["currencyType"] = currencyType
        this["amount"] = amount
        this["amountBonus"] = amountBonus
        this["moneySpent"] = moneySpent
        this["outsideWallet"] = isOutsideWallet
        transactions?.mapNotNull { it?.toFirestoreMap() }
            ?.let { this["transactions"] = it }
        if (this@toFirestoreMap is CCDBWallet) {
            this["transactionType"] = transactionType
        }
    }

/** The Firestore date object for a `java.util.Date` (millisecond precision). */
private fun dateToTimestamp(date: java.util.Date): Timestamp {
    val ms = date.time
    var seconds = ms / 1000
    var nanos = ((ms % 1000) * 1_000_000).toInt()
    if (nanos < 0) {
        nanos += 1_000_000_000
        seconds -= 1
    }
    return Timestamp(seconds, nanos)
}
