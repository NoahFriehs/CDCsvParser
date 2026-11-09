package at.msd.friehs_bicha.cdcsvparser.util

import android.widget.Toast
import at.msd.friehs_bicha.cdcsvparser.App
import at.msd.friehs_bicha.cdcsvparser.R
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Cloud-document maintenance runs (both are best-effort and NEVER
 * destroy the document: a write happens only after the payload was
 * fully resolved and encoded, so a failed run leaves the document
 * exactly as found).
 *
 * [runFirstLaunchMigration] - once per install (flag-backed): a signed-
 * in user whose cloud document is still a LEGACY plaintext document
 * (no cipher metadata) gets it re-written under the key model in force
 * - re-ENCRYPT only, the plaintext is already readable, so no decrypt
 * is needed. Documents that are already encrypted, or that cannot be
 * read, are left alone: the restore funnel reports the explicit
 * "unreadable" state where the data is fetched (the wipe + re-import
 * fallback), and the one-shot flag is set either way so the start-up
 * check does not retry forever.
 *
 * [reEncryptUnderCurrentSettings] - immediately after a key-model
 * switch or a password change (the settings already hold the NEW state
 * when this runs): fetch-old -> read under the OLD credentials (the
 * document's own key metadata, but the supplied pre-change password
 * for the password model) -> write under the NEW settings, so there is
 * no window in which the document is unreadable to its owner on
 * another device or a fresh install.
 */
object CloudMigration {

    private const val TAG = "CloudMigration"

    /** Bounded: maintenance must not pin a start-up coroutine forever. */
    private const val FETCH_TIMEOUT_MS = 60_000L

    /** What a maintenance run did (log-first; only the re-encrypt run toasts). */
    enum class Outcome {
        /** The one-shot flag was already set (no repeat run). */
        ALREADY_DONE,
        /** No signed-in user at this moment - retried on the next start. */
        SIGNED_OUT,
        /** The document does not exist (or the fetch failed) - retried. */
        NO_DOCUMENT,
        /** The document already carries the current cipher version. */
        NOTHING_ENCRYPTED,
        /** The document was re-written under the key model in force. */
        RE_ENCRYPTED,
        /** The document exists but is not readable - logged (and toasted
         * in the switch run); the restore funnel surfaces it where the
         * data is fetched. */
        UNREADABLE,
        /** No local key for the write (e.g. empty password in password
         * mode) - retriable on the next start. */
        FAILED,
    }

    /**
     * The one-shot, post-update start-up check (called from
     * `App.onCreate` on the IO pool): re-encrypts a legacy PLAINTEXT
     * cloud document under the key model in force. The flag is set
     * only after the document was seen and handled (or left alone on
     * purpose) - a signed-out start or a failed fetch simply retries
     * on the next launch.
     */
    suspend fun runFirstLaunchMigration(): Outcome {
        val context = App.instance
        if (PreferenceHelper.getCloudMigrationCompleted(context)) {
            return Outcome.ALREADY_DONE
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            FileLog.i(TAG, "Migration: not signed in, retrying on the next start")
            return Outcome.SIGNED_OUT
        }
        val doc = fetchUserDoc(user.uid)?.let { FirebaseUtil.toPlainByteDoc(it) }
        if (doc == null) {
            FileLog.i(TAG, "Migration: no document (or fetch failed), retrying on the next start")
            return Outcome.NO_DOCUMENT
        }
        when (CoreCipher.migrationAction(doc)) {
            CoreCipher.MigrationAction.NONE -> {
                PreferenceHelper.setCloudMigrationCompleted(context)
                FileLog.i(TAG, "Migration: document already encrypted (no action)")
                return Outcome.NOTHING_ENCRYPTED
            }

            CoreCipher.MigrationAction.UNREADABLE -> {
                // "Too new" or malformed cipher metadata: it cannot be
                // re-written (a re-encrypt needs a readable document),
                // and the restore funnel says the true thing where the
                // data is fetched.
                PreferenceHelper.setCloudMigrationCompleted(context)
                FileLog.e(TAG, "Migration: document unreadable (cipher metadata); the restore funnel will surface it")
                return Outcome.UNREADABLE
            }

            CoreCipher.MigrationAction.RE_ENCRYPT -> {
                val appSettings = doc["appSettings"] as? HashMap<String, Any>
                val payload = CoreCipher.legacyPayloadFromDoc(doc)
                if (appSettings == null || payload == null) {
                    // A shape this app never wrote (no settings or a
                    // non-list collection): the restore path cannot use
                    // it either - never write over a guess.
                    PreferenceHelper.setCloudMigrationCompleted(context)
                    FileLog.e(TAG, "Migration: legacy document without a usable payload (settings/collections malformed); left alone")
                    return Outcome.UNREADABLE
                }
                return finishReEncrypt(
                    user.uid,
                    appSettings,
                    payload,
                    context.getString(R.string.cloud_reencrypt_success),
                    context.getString(R.string.cloud_reencrypt_failed),
                    context,
                    onWritten = {
                        PreferenceHelper.setCloudMigrationCompleted(context)
                        FileLog.i(TAG, "Migration: legacy document re-encrypted under the key model in force")
                    },
                    onFailure = {
                        FileLog.e(TAG, "Migration: re-encrypt failed; retrying on the next start")
                    },
                )
            }
        }
    }

    /**
     * The immediate re-encrypt pass (the "no readability window"
     * ruling): after the settings were ALREADY flipped to the new key
     * model / password, this reads the current document under the OLD
     * credentials and writes it under the NEW settings. [oldPassword]
     * is the password the document was written with (the pre-change
     * value); it only matters for a document whose own key metadata
     * says "password". Signed-out / no-document starts are silent no-
     * ops (nothing exists to update); a document that cannot be read
     * is surfaced with a toast and left exactly as found.
     */
    suspend fun reEncryptUnderCurrentSettings(oldPassword: String): Outcome {
        val context = App.instance
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            FileLog.i(TAG, "Re-encrypt: not signed in, nothing on file to update")
            return Outcome.SIGNED_OUT
        }
        val doc = fetchUserDoc(user.uid)?.let { FirebaseUtil.toPlainByteDoc(it) }
        if (doc == null) {
            FileLog.i(TAG, "Re-encrypt: no document, nothing to update")
            return Outcome.NO_DOCUMENT
        }
        val appSettings: HashMap<String, Any>?
        val payload: Map<String, Any>
        when (CoreCipher.decideDocCipher(doc[CoreCipher.FIELD_CIPHER_VERSION])) {
            CoreCipher.DocCipherDecision.PlaintextLegacy -> {
                val settings = doc["appSettings"] as? HashMap<String, Any>
                val legacy = CoreCipher.legacyPayloadFromDoc(doc)
                if (settings == null || legacy == null) {
                    toastReEncryptFailure("legacy document without a usable payload")
                    return Outcome.UNREADABLE
                }
                appSettings = settings
                payload = legacy
            }

            is CoreCipher.DocCipherDecision.CiphertextTooNew,
            is CoreCipher.DocCipherDecision.CiphertextMalformed -> {
                // A newer build's document: the restore funnel carries
                // the "too new" text; a re-encrypt would lose it.
                toastReEncryptFailure("document is not readable by this version")
                return Outcome.UNREADABLE
            }

            CoreCipher.DocCipherDecision.CiphertextCurrent -> {
                when (
                    val resolved = CoreCipher.resolveDocPayload(doc) { model, salt, iterations ->
                        // Under the key the DOCUMENT records - but the
                        // pre-change password (the settings may already
                        // hold the new one by the time this runs).
                        when (model) {
                            CoreCipher.KEY_MODEL_KEYSTORE -> CoreCipher.getOrCreateKeystoreKey()

                            CoreCipher.KEY_MODEL_PASSWORD -> if (
                                oldPassword.isEmpty() || salt == null || iterations == null
                            ) {
                                null
                            } else {
                                CoreCipher.derivePasswordKey(oldPassword, salt, iterations)
                            }

                            else -> null
                        }
                    }
                ) {
                    is CoreCipher.DocPayloadResult.Ready -> {
                        val settings =
                            resolved.payload["appSettings"] as? HashMap<String, Any>
                        if (settings == null) {
                            toastReEncryptFailure("document without appSettings")
                            return Outcome.UNREADABLE
                        }
                        appSettings = settings
                        payload = resolved.payload
                    }

                    is CoreCipher.DocPayloadResult.Unreadable -> {
                        toastReEncryptFailure("${resolved.reason.name.lowercase()}")
                        return Outcome.UNREADABLE
                    }
                }
            }
        }
        return finishReEncrypt(
            user.uid,
            appSettings,
            payload,
            context.getString(R.string.cloud_reencrypt_success),
            context.getString(R.string.cloud_reencrypt_failed),
            context,
            onWritten = {
                FileLog.i(TAG, "Re-encrypt: document re-written under the key model in force")
            },
            onFailure = {
                // The task's completion listener already carries the user-
                // facing error toast - no second one here.
            },
        )
    }

    /**
     * The shared re-encrypt tail: build the canonical encrypted document
     * under the CURRENT settings (the key model preference + a fresh
     * random salt for the password model), write it (single full-
     * document `set`, SDK byte boundary handled inside) and AWAIT the
     * write - the caller scopes are serial (one re-encrypt at a time),
     * so the next run must only start after this one's document is on
     * file. The outcome is RE_ENCRYPTED only when the write LANDED
     * (a failure leaves the document as found and hands back the
     * failure outcome; the write task's own completion listener
     * carries the user-facing error toast).
     */
    private fun finishReEncrypt(
        uid: String,
        appSettings: HashMap<String, Any>?,
        payload: Map<String, Any>,
        successMessage: String,
        errorMessage: String,
        context: App,
        onWritten: () -> Unit,
        onFailure: () -> Unit,
    ): Outcome {
        val settings = appSettings
            ?: return Outcome.UNREADABLE
        val docFields = try {
            FirebaseUtil.buildEncryptedUserDoc(settings, payload)
        } catch (e: Exception) {
            // No local key for the settings' model (e.g. an empty
            // password while in password mode) - a state the user can
            // fix and that the next run retries.
            FileLog.e(TAG, "Re-encrypt: no local key for the key model in force (${e.message})")
            Toast.makeText(
                context, context.getString(R.string.cloud_reencrypt_failed), Toast.LENGTH_LONG
            ).show()
            onFailure()
            return Outcome.FAILED
        }
        return try {
            Tasks.await(
                FirebaseUtil.writeUserDoc(
                    uid,
                    Firebase.firestore,
                    docFields,
                    context,
                    successMessage,
                    errorMessage,
                )
            )
            onWritten()
            Outcome.RE_ENCRYPTED
        } catch (e: Exception) {
            FileLog.e(TAG, "Re-encrypt: the write failed (${e.message})")
            onFailure()
            Outcome.FAILED
        }
    }

    /** The one toast for the switch run's readable failure states. */
    private fun toastReEncryptFailure(detail: String) {
        FileLog.e(TAG, "Re-encrypt: document left unchanged ($detail)")
        Toast.makeText(
            App.instance,
            App.instance.getString(R.string.cloud_reencrypt_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    /**
     * The user document (top level, byte fields as the SDK returns
     * them) or null when the fetch failed / timed out / the document
     * does not exist (the funnel lumps the last two together; both
     * mean "nothing to act on, retry later").
     */
    private suspend fun fetchUserDoc(uid: String): HashMap<String, Any>? {
        val deferred = CompletableDeferred<HashMap<String, Any>?>()
        FirebaseUtil.getUserDataFromFirestore(uid, Firebase.firestore) { result ->
            deferred.complete(result.getOrNull())
        }
        return try {
            withTimeout(FETCH_TIMEOUT_MS) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            FileLog.e(TAG, "Timed out fetching the user document")
            null
        }
    }
}
