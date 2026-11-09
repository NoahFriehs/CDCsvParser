package at.msd.friehs_bicha.cdcsvparser.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import com.google.firebase.Timestamp
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Client-side encryption of the Firestore user document (AES-256-GCM).
 *
 * The financial payload (the wallet/transaction collections) leaves the
 * device only as ciphertext; every document records the metadata a decoder
 * needs: [FIELD_CIPHER_VERSION], [FIELD_KEY_MODEL] and - for the password
 * model - [FIELD_KDF_SALT]/[FIELD_KDF_ITERATIONS]. `appSettings` (incl. the
 * `dbVersion` compatibility marker) stays in the clear so the version gate
 * can run BEFORE any decrypt attempt.
 *
 * Key models:
 * - [KEY_MODEL_KEYSTORE]: a non-exportable AES-256 key generated once in
 *   the Android Keystore (see [getOrCreateKeystoreKey]); no key material
 *   ever leaves the device.
 * - [KEY_MODEL_PASSWORD]: an AES-256 key derived from the user's password
 *   via PBKDF2 (HMAC-SHA256, random 16-byte salt + [KDF_ITERATIONS], both
 *   stored in the document metadata), so only the password holder can
 *   decrypt.
 *
 * A document WITHOUT [FIELD_CIPHER_VERSION] is a legacy plaintext document:
 * the migration signal (this module only detects it, re-encrypting it is
 * the caller's job). A document WITH a HIGHER cipher version is "too new"
 * for this build and must be reported as such - never as "wrong key" - so
 * an old app reading a future document says the true thing.
 *
 * Pure JVM (no Android framework types): the Keystore access is the only
 * platform-bound part; unit tests substitute a plain JCE AES key for the
 * device-bound key, which exercises the exact same encrypt/decrypt path.
 */
object CoreCipher {

    private const val TAG = "CoreCipher"

    // The document cipher format (bump-forward: a higher value = a format
    // this build cannot read; see DocCipherDecision.CiphertextTooNew).
    const val CIPHER_VERSION = 1

    const val KEY_MODEL_KEYSTORE = "keystore"
    const val KEY_MODEL_PASSWORD = "password"
    val KEY_MODELS = setOf(KEY_MODEL_KEYSTORE, KEY_MODEL_PASSWORD)

    // Document field names (top level, next to `appSettings`).
    const val FIELD_PAYLOAD = "payloadCipher"
    const val FIELD_CIPHER_VERSION = "cipherVersion"
    const val FIELD_KEY_MODEL = "keyModel"
    const val FIELD_KDF_SALT = "kdfSalt"
    const val FIELD_KDF_ITERATIONS = "kdfIterations"

    // The five payload collections - exactly the top-level keys the
    // firestore-shaped document carries (and the restore path reads).
    val PAYLOAD_COLLECTIONS =
        listOf("wallets", "outsideWallets", "transactions", "cardWallets", "cardTransactions")

    const val KDF_ALGORITHM = "PBKDF2withHmacSHA256"
    const val KDF_SALT_BYTES = 16
    const val KDF_ITERATIONS = 210_000

    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128
    private val GCM_TAG_BYTES = GCM_TAG_BITS / 8

    // The AndroidKeyStore alias for the device-bound key (fixed: a reset
    // of the device key material loses the documents, the documented
    // fallback is wipe + re-import).
    const val KEYSTORE_ALIAS = "at.msd.friehs_bicha.cdcsvparser.cloud.v1"

    /** The cipher failed to authenticate - tampered or foreign cipher. */
    class CipherDecryptException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    /** The payload bytes are not a well-formed payload document. */
    class PayloadCodecException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    // ------------------------------------------------------------------
    // Document shape decision (pure)
    // ------------------------------------------------------------------

    sealed class DocCipherDecision {
        /** No cipher metadata: a legacy plaintext document (the migration signal). */
        data object PlaintextLegacy : DocCipherDecision()

        /** The cipher version this build implements: decrypt it. */
        data object CiphertextCurrent : DocCipherDecision()

        /** A later build's format: report "too new", never attempt a decrypt. */
        data class CiphertextTooNew(val version: Int) : DocCipherDecision()

        /** Cipher metadata present but malformed (wrong type / nonsense value). */
        data object CiphertextMalformed : DocCipherDecision()
    }

    /**
     * The version gate that runs NEXT TO (after) the existing `dbVersion`
     * string gate: absence of the metadata is the legacy/migrate signal,
     * a higher number must read as "too new" and not as "wrong key".
     */
    fun decideDocCipher(raw: Any?): DocCipherDecision = when (raw) {
        null -> DocCipherDecision.PlaintextLegacy
        is Number -> when (val version = raw.toInt()) {
            CIPHER_VERSION -> DocCipherDecision.CiphertextCurrent
            else -> if (version > CIPHER_VERSION) {
                DocCipherDecision.CiphertextTooNew(version)
            } else {
                DocCipherDecision.CiphertextMalformed
            }
        }

        else -> DocCipherDecision.CiphertextMalformed
    }

    /**
     * The first-launch action a fetched user document requires (the pure
     * decision piece; PERFORMING it - the re-encrypt pass and the error
     * surfacing at the first-start check - is the caller's job):
     * - [NONE]: a current-version ciphertext, the normal restore path.
     * - [RE_ENCRYPT]: no cipher metadata - a legacy plaintext document;
     *   it can be re-written under the current key model without a
     *   decrypt (re-ENCRYPT only, the plaintext is already readable).
     * - [UNREADABLE]: "too new" or malformed cipher metadata - the
     *   document must be reported, never guessed.
     */
    enum class MigrationAction { NONE, RE_ENCRYPT, UNREADABLE }

    /** Pins the decision for a fetched document map (fake-doc safe). */
    fun migrationAction(doc: Map<String, Any?>): MigrationAction =
        when (decideDocCipher(doc[FIELD_CIPHER_VERSION])) {
            DocCipherDecision.PlaintextLegacy -> MigrationAction.RE_ENCRYPT
            DocCipherDecision.CiphertextCurrent -> MigrationAction.NONE
            is DocCipherDecision.CiphertextTooNew,
            DocCipherDecision.CiphertextMalformed -> MigrationAction.UNREADABLE
        }

    /**
     * The explicit no-decrypt error states. A restore may never fall back
     * to a partial load: every one of these is surfaced to the user
     * (wipe + re-import, or update the app) instead of an empty core.
     */
    enum class UnreadableReason {
        CIPHER_TOO_NEW,
        CIPHER_MALFORMED,
        /** Cipher metadata missing/incomplete or no local key for it. */
        METADATA_MISSING,
        /** GCM authentication failed: tampered or foreign ciphertext. */
        DECRYPT_FAILED,
        /** Decrypted bytes are not a well-formed payload document. */
        PAYLOAD_MALFORMED,
    }

    sealed class DocPayloadResult {
        /**
         * The readable document: the legacy plaintext as-is (the restore
         * path's existing map read) or the user map with the ciphertext
         * fields replaced by the decrypted payload collections.
         */
        class Ready(val payload: Map<String, Any>, val wasPlaintext: Boolean) : DocPayloadResult()

        class Unreadable(val reason: UnreadableReason) : DocPayloadResult()
    }

    /**
     * Reads the cipher layer of a fetched user document and yields the
     * document the restore path should parse.
     *
     * [keyResolver] maps the DOCUMENT'S key metadata to the local key (or
     * null when the device has no key for it - e.g. a wiped keystore or
     * no stored password); it is only invoked for a current-version
     * ciphertext, never for legacy/too-new documents.
     */
    fun resolveDocPayload(
        doc: Map<String, Any?>,
        keyResolver: (keyModel: String, kdfSalt: ByteArray?, kdfIterations: Int?) -> SecretKey?,
    ): DocPayloadResult = when (val decision = decideDocCipher(doc[FIELD_CIPHER_VERSION])) {
        DocCipherDecision.PlaintextLegacy ->
            DocPayloadResult.Ready(java.util.HashMap(doc), wasPlaintext = true)

        is DocCipherDecision.CiphertextTooNew ->
            DocPayloadResult.Unreadable(UnreadableReason.CIPHER_TOO_NEW)

        DocCipherDecision.CiphertextMalformed ->
            DocPayloadResult.Unreadable(UnreadableReason.CIPHER_MALFORMED)

        DocCipherDecision.CiphertextCurrent -> {
            val keyModel = doc[FIELD_KEY_MODEL] as? String
                ?: return DocPayloadResult.Unreadable(UnreadableReason.METADATA_MISSING)
            if (keyModel !in KEY_MODELS) {
                return DocPayloadResult.Unreadable(UnreadableReason.METADATA_MISSING)
            }
            val kdfSalt = if (keyModel == KEY_MODEL_PASSWORD) {
                bytesOrNull(doc[FIELD_KDF_SALT])
                    ?: return DocPayloadResult.Unreadable(UnreadableReason.METADATA_MISSING)
            } else {
                null
            }
            val kdfIterations = if (keyModel == KEY_MODEL_PASSWORD) {
                (doc[FIELD_KDF_ITERATIONS] as? Number)?.toInt()
                    ?: return DocPayloadResult.Unreadable(UnreadableReason.METADATA_MISSING)
            } else {
                null
            }
            val key = keyResolver(keyModel, kdfSalt, kdfIterations)
                ?: return DocPayloadResult.Unreadable(UnreadableReason.METADATA_MISSING)
            val blob = bytesOrNull(doc[FIELD_PAYLOAD])
                ?: return DocPayloadResult.Unreadable(UnreadableReason.METADATA_MISSING)
            val plain = try {
                decrypt(key, blob)
            } catch (e: CipherDecryptException) {
                // The cipher layer failed its authentication: the content
                // is unreadable (tampered, or produced by another key),
                // and NOTHING of it may leak into a partial load.
                return DocPayloadResult.Unreadable(UnreadableReason.DECRYPT_FAILED)
            }
            val payload = try {
                decodePayload(plain)
            } catch (e: PayloadCodecException) {
                return DocPayloadResult.Unreadable(UnreadableReason.PAYLOAD_MALFORMED)
            }
            // The user map with the cipher fields replaced by the
            // decrypted collections - the restore path reads the same
            // top-level keys as from a legacy document.
            val merged = java.util.HashMap<String, Any>(doc)
            merged.remove(FIELD_PAYLOAD)
            merged.remove(FIELD_KEY_MODEL)
            merged.remove(FIELD_KDF_SALT)
            merged.remove(FIELD_KDF_ITERATIONS)
            merged.putAll(payload)
            DocPayloadResult.Ready(merged, wasPlaintext = false)
        }
    }

    // ------------------------------------------------------------------
    // Key derivation (pure JCE)
    // ------------------------------------------------------------------

    /** Derives the password-model AES-256 key (PBKDF2 / HMAC-SHA256). */
    fun derivePasswordKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        require(iterations >= 1) { "KDF iterations must be positive" }
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val derived = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec)
        // The KDF hands the raw 256-bit material back under its own
        // "PBKDF2..." algorithm name, which a Cipher init refuses - re-wrap
        // it as the AES key it is.
        return SecretKeySpec(derived.encoded, "AES")
    }

    /** A fresh random KDF salt (stored in the document metadata per write). */
    fun randomKdfSalt(): ByteArray = ByteArray(KDF_SALT_BYTES).also { SecureRandom().nextBytes(it) }

    // ------------------------------------------------------------------
    // AES-256-GCM (pure JCE)
    // ------------------------------------------------------------------

    /**
     * Encrypts with a fresh random 12-byte IV per write. Output layout:
     * IV (12 bytes) || ciphertext || GCM tag (16 bytes).
     */
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Reverses [encrypt]. A tampered or foreign ciphertext fails GCM
     * authentication and surfaces as [CipherDecryptException] - the ONLY
     * failure state of this function (never a partial / silent result).
     */
    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        if (blob.size <= GCM_IV_BYTES + GCM_TAG_BYTES) {
            throw CipherDecryptException("ciphertext shorter than IV + tag")
        }
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return try {
            cipher.doFinal(blob.copyOfRange(GCM_IV_BYTES, blob.size))
        } catch (e: GeneralSecurityException) {
            throw CipherDecryptException("GCM authentication failed", e)
        }
    }

    // ------------------------------------------------------------------
    // Document field assembly (pure)
    // ------------------------------------------------------------------

    /**
     * The cipher fields of a new user document for the given [payload]
     * (the five collections). The password model stores a FRESH random
     * salt per write; the keystore model stores none (the key is
     * device-bound and the document is self-describing).
     */
    fun buildEncryptedDocFields(
        payload: Map<String, Any?>,
        key: SecretKey,
        keyModel: String,
        kdfSalt: ByteArray? = null,
        kdfIterations: Int? = null,
    ): HashMap<String, Any> {
        require(keyModel in KEY_MODELS) { "unknown key model: $keyModel" }
        require((keyModel == KEY_MODEL_PASSWORD) == (kdfSalt != null && kdfIterations != null)) {
            "password model and KDF metadata must both be present or both absent"
        }
        val fields = HashMap<String, Any>()
        fields[FIELD_PAYLOAD] = encrypt(key, encodePayload(payload))
        fields[FIELD_CIPHER_VERSION] = CIPHER_VERSION
        fields[FIELD_KEY_MODEL] = keyModel
        if (kdfSalt != null) {
            fields[FIELD_KDF_SALT] = kdfSalt
            fields[FIELD_KDF_ITERATIONS] = kdfIterations!!
        }
        return fields
    }

    // ------------------------------------------------------------------
    // Payload codec (pure) - the firestore-shaped map <-> JSON bytes
    // ------------------------------------------------------------------

    /**
     * Serializes the firestore-shaped payload map (the five collections
     * of primitive-typed entry maps - exactly what the Firestore readers
     * consume after a round trip) into UTF-8 JSON bytes. Dates ride as
     * millisecond numbers (the values the app writes always have
     * millisecond precision).
     */
    fun encodePayload(payload: Map<String, Any?>): ByteArray {
        for (collection in PAYLOAD_COLLECTIONS) {
            if (payload[collection] !is List<*>) {
                throw PayloadCodecException("payload collection missing or wrong type: $collection")
            }
        }
        val root = buildJsonObject {
            for (collection in PAYLOAD_COLLECTIONS) {
                put(
                    collection,
                    JsonArray((payload[collection] as List<*>).map { elementOf(it) }),
                )
            }
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    /** Reverses [encodePayload] into the firestore-shaped map (Java collections). */
    fun decodePayload(bytes: ByteArray): HashMap<String, Any> {
        val root = try {
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw PayloadCodecException("payload is not well-formed JSON", e)
        }
        val rootObject = root as? JsonObject
            ?: throw PayloadCodecException("payload root is not an object")
        val out = HashMap<String, Any>()
        for (collection in PAYLOAD_COLLECTIONS) {
            val array = rootObject[collection] as? JsonArray
                ?: throw PayloadCodecException("payload collection missing: $collection")
            val list = java.util.ArrayList<HashMap<String, *>>()
            for (element in array) {
                list.add(objectToMap(element))
            }
            out[collection] = list
        }
        return out
    }

    private fun elementOf(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Byte, is Short, is Int, is Long -> JsonPrimitive((value as Number).toLong())
        is Float -> JsonPrimitive(value.toDouble())
        is Double -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value.toLong())
        is Timestamp -> JsonPrimitive(value.toDate().time)
        is java.util.Date -> JsonPrimitive(value.time)
        is Map<*, *> -> buildJsonObject {
            for ((key, entry) in value) put(key.toString(), elementOf(entry))
        }

        is List<*> -> JsonArray(value.map { elementOf(it) })
        else -> throw PayloadCodecException(
            "unsupported payload value type: ${value.javaClass.name}"
        )
    }

    private fun objectToMap(element: JsonElement): HashMap<String, Any?> {
        val obj = element as? JsonObject
            ?: throw PayloadCodecException("payload entry is not an object")
        val out = HashMap<String, Any?>()
        for ((key, entry) in obj) {
            out[key] = valueOf(entry)
        }
        // Transaction entries (top-level AND nested inside wallets) carry
        // `date` as milliseconds; the restore readers expect the Firestore
        // date object there. Wallet entries carry no `date` key, so the key
        // test alone is sufficient.
        (out["date"] as? Number)?.let { millis -> out["date"] = dateFromMillis(millis) }
        return out
    }

    private fun valueOf(element: JsonElement): Any? = when {
        element is JsonNull -> null
        element is JsonObject -> objectToMap(element)
        element is JsonArray -> {
            val list = java.util.ArrayList<Any?>()
            element.forEach { list.add(valueOf(it)) }
            list
        }

        element is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull!!
            element.content.toIntOrNull() != null -> element.content.toInt()
            element.content.toLongOrNull() != null -> element.content.toLong()
            element.content.toDoubleOrNull() != null -> element.content.toDouble()
            else -> throw PayloadCodecException(
                "unsupported JSON primitive: ${element.content}"
            )
        }

        // JsonLiteral subclasses that are not JsonPrimitive (the sealed
        // JsonElement hierarchy leaves no other branch, but the check must
        // be total).
        else -> throw PayloadCodecException("unsupported JSON element: $element")
    }

    // ------------------------------------------------------------------
    // The device-bound key (Android-only; never exercised in JVM tests)
    // ------------------------------------------------------------------

    @Volatile
    private var keystoreKey: SecretKey? = null

    /**
     * The non-exportable AndroidKeyStore AES-256 key (generated once at
     * the fixed alias [KEYSTORE_ALIAS]). Returns null when the platform
     * refuses (e.g. a JVM unit test) - the caller treats that as
     * "no local key" (the METADATA_MISSING error state).
     */
    fun getOrCreateKeystoreKey(): SecretKey? {
        keystoreKey?.let { return it }
        val key = try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)
                ?.secretKey
            if (existing != null) {
                existing
            } else {
                // Keystore secret keys are non-exportable by construction
                // (the material never leaves the secure hardware), so no
                // extra flag is needed for that.
                KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
                    init(
                        KeyGenParameterSpec.Builder(
                            KEYSTORE_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or
                                KeyProperties.PURPOSE_DECRYPT,
                        )
                            .setKeySize(256)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build(),
                        SecureRandom(),
                    )
                }.generateKey()
            }
        } catch (e: Exception) {
            FileLog.e(TAG, "Keystore key unavailable: ${e.message}")
            return null
        }
        keystoreKey = key
        return key
    }

    // ------------------------------------------------------------------

    /** Accepts the byte-ish representations Firestore may hand back. */
    internal fun bytesOrNull(value: Any?): ByteArray? = when (value) {
        null -> null
        is ByteArray -> value
        is List<*> -> if (value.all { it is Number }) {
            value.map { (it as Number).toByte() }.toByteArray()
        } else {
            null
        }

        is java.nio.ByteBuffer -> {
            val bytes = ByteArray(value.remaining())
            value.duplicate().get(bytes)
            bytes
        }

        else -> null
    }

    /** The Firestore date object for milliseconds since the epoch. */
    private fun dateFromMillis(millis: Number): Timestamp {
        val ms = millis.toLong()
        var seconds = ms / 1000
        var nanos = ((ms % 1000) * 1_000_000).toInt()
        if (nanos < 0) {
            nanos += 1_000_000_000
            seconds -= 1
        }
        return Timestamp(seconds, nanos)
    }
}
