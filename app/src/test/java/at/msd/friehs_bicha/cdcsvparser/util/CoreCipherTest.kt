package at.msd.friehs_bicha.cdcsvparser.util

import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher.CipherDecryptException
import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher.DocCipherDecision
import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher.DocPayloadResult
import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher.KEY_MODEL_KEYSTORE
import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher.KEY_MODEL_PASSWORD
import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher.MigrationAction
import at.msd.friehs_bicha.cdcsvparser.util.CoreCipher.UnreadableReason
import com.google.firebase.firestore.Blob
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Date
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Pins the Firestore payload cipher (the pure JCE half). The Android
 * Keystore key cannot exist in a JVM test, so the keystore model is
 * exercised through a plain 256-bit AES key - the EXACT same
 * encrypt/decrypt path the device-bound key takes; the password model
 * runs its real PBKDF2 derivation. Pinned: the AES-256-GCM round trip
 * (both key models), tamper / wrong-key refusal as an explicit error
 * state (never a partial load, never an escaped GCM exception), KDF
 * salt + iteration handling, the cipherVersion gate (no version = the
 * migration signal, a future version = the explicit "too new" error),
 * and the pure migration decision (doc map -> action) against fake
 * docs.
 */
class CoreCipherTest {

    /** A plain 256-bit AES key (the keystore stand-in, same cipher path). */
    private fun aesKey(seed: Long): SecretKey {
        val random = SecureRandom()
        random.setSeed(seed)
        return KeyGenerator.getInstance("AES").apply {
            init(256, random)
        }.generateKey()
    }

    private val KEY_A = aesKey(1L)
    private val KEY_B = aesKey(2L)

    /** The Firestore-shaped payload (the five collections, primitives). */
    private fun payloadFixture(): Map<String, Any?> {
        val txDate = Date(1_730_000_000_123L)
        return mapOf(
            "wallets" to listOf(
                hashMapOf<String, Any>(
                    "walletId" to 3L,
                    "currencyType" to "BTC",
                    "amount" to 1.25,
                    "amountBonus" to 0.0,
                    "moneySpent" to 42.0,
                    "outsideWallet" to false,
                    "transactions" to emptyList<Any>(),
                ),
            ),
            "outsideWallets" to emptyList<Any>(),
            "transactions" to listOf(
                hashMapOf<String, Any?>(
                    "transactionId" to 17L,
                    "description" to "Mining",
                    "walletId" to 3,
                    "fromWalletId" to 0,
                    "date" to txDate,
                    "currencyType" to "BTC",
                    "amount" to -0.5,
                    "nativeAmount" to -2100.5,
                    "amountBonus" to 0.0,
                    "transactionType" to "deposit",
                    "transHash" to null,
                    "toCurrency" to null,
                    "toAmount" to null,
                    "outsideTransaction" to false,
                ),
            ),
            "cardWallets" to listOf(
                hashMapOf<String, Any>(
                    "walletId" to 9L,
                    "currencyType" to "EUR",
                    "amount" to 0.0,
                    "amountBonus" to 0.0,
                    "moneySpent" to 11.99,
                    "outsideWallet" to false,
                    "transactions" to emptyList<Any>(),
                    "transactionType" to "Supermarket",
                ),
            ),
            "cardTransactions" to emptyList<Any>(),
        )
    }

    private fun settingsFixture(): HashMap<String, Any> =
        hashMapOf(
            "userID" to "uid-1",
            "appType" to "CdCsvParser",
            "useStrictType" to false,
            "hasCryptoTx" to "true",
            "hasCardTx" to "true",
            "dbVersion" to "1.0.0",
        )

    /** A full fake user document for the keystore model. */
    private fun fakeKeystoreDoc(): Map<String, Any?> =
        hashMapOf<String, Any?>("appSettings" to settingsFixture()).apply {
            putAll(
                CoreCipher.buildEncryptedDocFields(payloadFixture(), KEY_A, KEY_MODEL_KEYSTORE)
            )
        }

    /** A document map whose only payload field is the given blob. */
    private fun docWithBlob(blob: ByteArray): Map<String, Any?> = hashMapOf(
        "appSettings" to HashMap<String, Any>(),
        CoreCipher.FIELD_PAYLOAD to blob,
        CoreCipher.FIELD_CIPHER_VERSION to CoreCipher.CIPHER_VERSION,
        CoreCipher.FIELD_KEY_MODEL to KEY_MODEL_KEYSTORE,
    )

    private val keystoreResolver: (String, ByteArray?, Int?) -> SecretKey? =
        { model, _, _ -> if (model == KEY_MODEL_KEYSTORE) KEY_A else null }

    // ------------------------------------------------------------------
    // AES-256-GCM round trip (both key models)
    // ------------------------------------------------------------------

    @Test
    fun keystoreModelRoundTripsThroughTheDoc() {
        val doc = fakeKeystoreDoc()
        val ready = CoreCipher.resolveDocPayload(doc, keystoreResolver) as? DocPayloadResult.Ready
            ?: error("expected Ready, got $doc resolution")
        assertFalse(ready.wasPlaintext)
        // The clear settings pass through; the ciphertext fields do not.
        assertEquals(settingsFixture(), ready.payload["appSettings"])
        assertFalse(ready.payload.containsKey(CoreCipher.FIELD_PAYLOAD))
        assertFalse(ready.payload.containsKey(CoreCipher.FIELD_KEY_MODEL))
        // The five collections come back in the firestore shape.
        for (collection in CoreCipher.PAYLOAD_COLLECTIONS) {
            assertNotNull(ready.payload[collection])
        }
        val first = (ready.payload["transactions"] as List<*>)[0] as Map<*, *>
        assertEquals("Mining", first["description"])
        assertEquals(17L, (first["transactionId"] as Number).toLong())
    }

    @Test
    fun passwordModelRoundTripsThroughTheDoc() {
        val password = "correct-horse-battery"
        val salt = CoreCipher.randomKdfSalt()
        val key = CoreCipher.derivePasswordKey(password, salt, CoreCipher.KDF_ITERATIONS)
        val doc = hashMapOf<String, Any?>(
            "appSettings" to settingsFixture(),
        ).apply {
            putAll(
                CoreCipher.buildEncryptedDocFields(
                    payloadFixture(), key, KEY_MODEL_PASSWORD, salt, CoreCipher.KDF_ITERATIONS,
                )
            )
        }
        // The doc is self-describing: the resolver re-derives the key
        // from the password + the DOCUMENT'S salt + iteration count.
        val ready = CoreCipher.resolveDocPayload(doc) { model, docSalt, iterations ->
            if (model == KEY_MODEL_PASSWORD && docSalt != null) {
                CoreCipher.derivePasswordKey(password, docSalt, iterations ?: error("iterations"))
            } else {
                null
            }
        } as? DocPayloadResult.Ready ?: error("expected Ready")
        assertFalse(ready.wasPlaintext)
        val first = (ready.payload["transactions"] as List<*>)[0] as Map<*, *>
        assertEquals("Mining", first["description"])
    }

    @Test
    fun eachWriteUsesAFreshIv() {
        val plain = "the same payload".toByteArray()
        val first = CoreCipher.encrypt(KEY_A, plain)
        val second = CoreCipher.encrypt(KEY_A, plain)
        assertTrue(!first.contentEquals(second))          // the fresh IVs differ
        assertArrayEquals(plain, CoreCipher.decrypt(KEY_A, first))
        assertArrayEquals(plain, CoreCipher.decrypt(KEY_A, second))
    }

    // ------------------------------------------------------------------
    // Tamper / foreign-key refusal (error state, never a partial load)
    // ------------------------------------------------------------------

    @Test
    fun tamperedCiphertextIsAnExplicitRefusal() {
        val blob = CoreCipher.encrypt(KEY_A, "secret payload".toByteArray())
        val tagTampered = blob.copyOf().also {
            it[blob.size - 1] = (it[blob.size - 1].toInt() xor 1).toByte()
        }
        val bodyTampered = blob.copyOf().also {
            it[CoreCipher.GCM_IV_BYTES] = (it[CoreCipher.GCM_IV_BYTES].toInt() xor 1).toByte()
        }
        assertThrows(CipherDecryptException::class.java) { CoreCipher.decrypt(KEY_A, tagTampered) }
        assertThrows(CipherDecryptException::class.java) { CoreCipher.decrypt(KEY_A, bodyTampered) }
        // Through the doc layer: an explicit error state, no partial load.
        val result = CoreCipher.resolveDocPayload(docWithBlob(tagTampered), keystoreResolver)
        val unreadable = result as? DocPayloadResult.Unreadable
            ?: error("expected Unreadable, got $result")
        assertEquals(UnreadableReason.DECRYPT_FAILED, unreadable.reason)
    }

    @Test
    fun aForeignKeyIsRefusedLikewise() {
        val blob = CoreCipher.encrypt(KEY_A, "secret".toByteArray())
        assertThrows(CipherDecryptException::class.java) { CoreCipher.decrypt(KEY_B, blob) }
        val result = CoreCipher.resolveDocPayload(
            docWithBlob(blob),
            { model, _, _ -> if (model == KEY_MODEL_KEYSTORE) KEY_B else null },
        )
        val unreadable = result as? DocPayloadResult.Unreadable
            ?: error("expected Unreadable, got $result")
        assertEquals(UnreadableReason.DECRYPT_FAILED, unreadable.reason)
    }

    @Test
    fun aBlobShorterThanIvPlusTagIsRefused() {
        assertThrows(CipherDecryptException::class.java) { CoreCipher.decrypt(KEY_A, ByteArray(10)) }
        assertThrows(CipherDecryptException::class.java) {
            CoreCipher.decrypt(KEY_A, ByteArray(CoreCipher.GCM_IV_BYTES + 16))
        }
    }

    // ------------------------------------------------------------------
    // KDF (password model) salt + iteration handling
    // ------------------------------------------------------------------

    @Test
    fun kdfSaltsAreRandomSixteenBytes() {
        val one = CoreCipher.randomKdfSalt()
        val two = CoreCipher.randomKdfSalt()
        assertEquals(CoreCipher.KDF_SALT_BYTES, one.size)
        assertTrue(!one.contentEquals(two))
    }

    @Test
    fun kdfIsDeterministicAndSaltAndPasswordSensitive() {
        val salt = ByteArray(CoreCipher.KDF_SALT_BYTES) { index -> index.toByte() }
        val same = CoreCipher.derivePasswordKey("pw", salt, 1_000)
        val again = CoreCipher.derivePasswordKey("pw", salt, 1_000)
        val otherSalt = ByteArray(CoreCipher.KDF_SALT_BYTES) { index -> (index + 1).toByte() }
        val salted = CoreCipher.derivePasswordKey("pw", otherSalt, 1_000)
        val otherPassword = CoreCipher.derivePasswordKey("pw2", salt, 1_000)
        assertArrayEquals(same.encoded, again.encoded)
        assertTrue(!same.encoded.contentEquals(salted.encoded))
        assertTrue(!same.encoded.contentEquals(otherPassword.encoded))
        assertEquals(32, same.encoded.size)   // AES-256 key material
    }

    @Test(expected = IllegalArgumentException::class)
    fun thePasswordModelRequiresKdfMetadata() {
        CoreCipher.buildEncryptedDocFields(payloadFixture(), KEY_A, KEY_MODEL_PASSWORD)
    }

    @Test(expected = IllegalArgumentException::class)
    fun theKeystoreModelMustNotCarryKdfMetadata() {
        CoreCipher.buildEncryptedDocFields(
            payloadFixture(), KEY_A, KEY_MODEL_KEYSTORE, ByteArray(CoreCipher.KDF_SALT_BYTES),
            CoreCipher.KDF_ITERATIONS,
        )
    }

    // ------------------------------------------------------------------
    // The cipherVersion gate (pre-decrypt, next to the clear dbVersion)
    // ------------------------------------------------------------------

    @Test
    fun aDocWithoutCipherVersionIsTheMigrationSignal() {
        val legacy: Map<String, Any?> = hashMapOf(
            "appSettings" to settingsFixture(),
            "wallets" to ArrayList<Any>(),
        )
        assertEquals(DocCipherDecision.PlaintextLegacy, CoreCipher.decideDocCipher(null))
        val result = CoreCipher.resolveDocPayload(legacy, keystoreResolver)
        val ready = result as? DocPayloadResult.Ready
            ?: error("expected Ready, got $result")
        assertTrue(ready.wasPlaintext)
        assertEquals(legacy["appSettings"], ready.payload["appSettings"])
        // The legacy doc reads through EXACTLY as-is (no coercion).
        assertEquals(legacy["wallets"], ready.payload["wallets"])
    }

    @Test
    fun aTooNewCipherVersionIsAnExplicitError() {
        val tooNew: Map<String, Any?> = docWithBlob(CoreCipher.encrypt(KEY_A, "x".toByteArray()))
            .let { base ->
                val copy = HashMap<String, Any?>(base)
                copy[CoreCipher.FIELD_CIPHER_VERSION] = 2
                copy
            }
        assertEquals(DocCipherDecision.CiphertextTooNew(2), CoreCipher.decideDocCipher(2))
        assertEquals(MigrationAction.UNREADABLE, CoreCipher.migrationAction(tooNew))
        var resolverCalled = false
        val result = CoreCipher.resolveDocPayload(tooNew) { _, _, _ ->
            resolverCalled = true
            KEY_A
        }
        val unreadable = result as? DocPayloadResult.Unreadable
            ?: error("expected Unreadable, got $result")
        assertEquals(UnreadableReason.CIPHER_TOO_NEW, unreadable.reason)
        assertFalse(resolverCalled)    // a future format is never even attempted
    }

    @Test
    fun aMalformedCipherVersionIsRefused() {
        assertEquals(DocCipherDecision.CiphertextMalformed, CoreCipher.decideDocCipher("1"))
        assertEquals(DocCipherDecision.CiphertextMalformed, CoreCipher.decideDocCipher(0))
        val doc: Map<String, Any?> = hashMapOf(CoreCipher.FIELD_CIPHER_VERSION to "1")
        assertEquals(MigrationAction.UNREADABLE, CoreCipher.migrationAction(doc))
        val result = CoreCipher.resolveDocPayload(doc, keystoreResolver)
        val unreadable = result as? DocPayloadResult.Unreadable
            ?: error("expected Unreadable, got $result")
        assertEquals(UnreadableReason.CIPHER_MALFORMED, unreadable.reason)
    }

    @Test
    fun theMigrationDecisionIsPinnedPerDocKind() {
        // No metadata at all: the legacy plaintext doc -> re-encrypt.
        assertEquals(MigrationAction.RE_ENCRYPT, CoreCipher.migrationAction(HashMap()))
        // The current version: the plain restore path.
        assertEquals(
            MigrationAction.NONE,
            CoreCipher.migrationAction(
                hashMapOf<String, Any?>(CoreCipher.FIELD_CIPHER_VERSION to CoreCipher.CIPHER_VERSION),
            ),
        )
        // A future version or a nonsense value: report, never guess.
        assertEquals(
            MigrationAction.UNREADABLE,
            CoreCipher.migrationAction(hashMapOf<String, Any?>(CoreCipher.FIELD_CIPHER_VERSION to 99)),
        )
        assertEquals(
            MigrationAction.UNREADABLE,
            CoreCipher.migrationAction(hashMapOf<String, Any?>(CoreCipher.FIELD_CIPHER_VERSION to "one")),
        )
    }

    // ------------------------------------------------------------------
    // Legacy plaintext documents (the migration's re-encrypt source)
    // ------------------------------------------------------------------

    @Test
    fun theLegacyPayloadReadsTheTopLevelCollections() {
        // A complete pre-encryption document: the five collections ride
        // top level and survive the extraction untouched.
        val wallets = listOf(hashMapOf<String, Any>("walletId" to 1L))
        val txs = listOf(hashMapOf<String, Any>("transactionId" to 7L))
        val doc: Map<String, Any?> = hashMapOf(
            "appSettings" to settingsFixture(),
            "wallets" to wallets,
            "outsideWallets" to emptyList<Any>(),
            "transactions" to txs,
            "cardWallets" to emptyList<Any>(),
            "cardTransactions" to emptyList<Any>(),
        )
        val payload = CoreCipher.legacyPayloadFromDoc(doc)
            ?: error("expected a legacy payload, got null")
        assertSame(wallets, payload["wallets"])
        assertSame(txs, payload["transactions"])
        // The re-encrypt write builds directly from this map: it must be
        // encodable as-is.
        CoreCipher.encodePayload(payload)
    }

    @Test
    fun aWalletEraLegacyDocReadsMissingCollectionsAsEmpty() {
        // Documents written before the card half existed carry no card
        // collections: the missing keys read as empty lists.
        val doc: Map<String, Any?> = hashMapOf(
            "appSettings" to settingsFixture(),
            "wallets" to listOf(hashMapOf<String, Any>("walletId" to 1L)),
            "outsideWallets" to emptyList<Any>(),
            "transactions" to emptyList<Any>(),
        )
        val payload = CoreCipher.legacyPayloadFromDoc(doc)
            ?: error("expected a legacy payload, got null")
        assertEquals(0, (payload["cardWallets"] as List<*>).size)
        assertEquals(0, (payload["cardTransactions"] as List<*>).size)
    }

    @Test
    fun aNonListCollectionRefusesTheLegacyPayload() {
        // A present collection of the wrong type is a shape this app
        // never wrote: no extraction (the document is never re-written
        // over a guess).
        val doc: Map<String, Any?> = hashMapOf(
            "appSettings" to settingsFixture(),
            "wallets" to hashMapOf<String, Any>("bogus" to 1L),
        )
        assertNull(CoreCipher.legacyPayloadFromDoc(doc))
    }

    // ------------------------------------------------------------------
    // Metadata / payload error states
    // ------------------------------------------------------------------

    @Test
    fun incompleteCipherMetadataRefusesWithoutDecryption() {
        val noModel: Map<String, Any?> = hashMapOf(
            CoreCipher.FIELD_CIPHER_VERSION to CoreCipher.CIPHER_VERSION,
            CoreCipher.FIELD_PAYLOAD to CoreCipher.encrypt(KEY_A, "x".toByteArray()),
        )
        val noPayload: Map<String, Any?> = hashMapOf(
            CoreCipher.FIELD_CIPHER_VERSION to CoreCipher.CIPHER_VERSION,
            CoreCipher.FIELD_KEY_MODEL to KEY_MODEL_KEYSTORE,
        )
        val passwordWithoutSalt: Map<String, Any?> = hashMapOf(
            CoreCipher.FIELD_CIPHER_VERSION to CoreCipher.CIPHER_VERSION,
            CoreCipher.FIELD_KEY_MODEL to KEY_MODEL_PASSWORD,
            CoreCipher.FIELD_PAYLOAD to ByteArray(64),
        )
        val noLocalKey = docWithBlob(CoreCipher.encrypt(KEY_A, "x".toByteArray()))
        for (doc in listOf(noModel, noPayload, passwordWithoutSalt, noLocalKey)) {
            val result = CoreCipher.resolveDocPayload(doc) { _, _, _ -> null }
            val unreadable = result as? DocPayloadResult.Unreadable
                ?: error("expected Unreadable for $doc, got $result")
            assertEquals(UnreadableReason.METADATA_MISSING, unreadable.reason)
        }
    }

    @Test
    fun anAuthenticatedButGarbagePayloadIsRefused() {
        // GCM passes; the codec must still refuse bytes that are not
        // the payload document.
        val garbage = CoreCipher.encrypt(KEY_A, byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(
            UnreadableReason.PAYLOAD_MALFORMED,
            (CoreCipher.resolveDocPayload(docWithBlob(garbage), keystoreResolver)
                as DocPayloadResult.Unreadable).reason,
        )
        // Well-formed JSON that still is not the five-collection shape.
        val partialJson = """{"wallets": []}""".toByteArray()
        assertEquals(
            UnreadableReason.PAYLOAD_MALFORMED,
            (CoreCipher.resolveDocPayload(docWithBlob(CoreCipher.encrypt(KEY_A, partialJson)),
                keystoreResolver) as DocPayloadResult.Unreadable).reason,
        )
    }

    // ------------------------------------------------------------------
    // Codec details (firestore-shaped entry maps)
    // ------------------------------------------------------------------

    @Test
    fun aTransactionDateRoundTripsWithMillisecondPrecision() {
        val millis = 1_730_000_000_123L
        val payload = payloadFixture().toMutableMap().apply {
            put("transactions", listOf(hashMapOf<String, Any>("date" to Date(millis))))
        }
        val decoded = CoreCipher.decodePayload(CoreCipher.encodePayload(payload))
        val entry = (decoded["transactions"] as List<*>)[0] as Map<*, *>
        val date = entry["date"] as? com.google.firebase.Timestamp
            ?: error("expected a firebase Timestamp, got ${entry["date"]}")
        assertEquals(millis, date.toDate().time)
    }

    @Test
    fun byteishRepresentationsResolveToPlainArrays() {
        val bytes = byteArrayOf(1, -2, 3)
        assertArrayEquals(bytes, CoreCipher.bytesOrNull(bytes))
        assertArrayEquals(bytes, CoreCipher.bytesOrNull(listOf(1, 254, 3)))
        assertArrayEquals(bytes, CoreCipher.bytesOrNull(java.nio.ByteBuffer.wrap(bytes)))
        assertNull(CoreCipher.bytesOrNull("nope"))
        assertNull(CoreCipher.bytesOrNull(null))
    }

    @Test
    fun theSdKBlobCrossesTheFunnelBoundaryAsPlainBytes() {
        // Read direction: the SDK hands the cipher its byte fields as
        // Blob; the funnel normalizes them to plain arrays.
        val fetched = mapOf<String, Any>(
            "appSettings" to settingsFixture(),
            CoreCipher.FIELD_PAYLOAD to Blob.fromBytes(byteArrayOf(9, 8, 7)),
            CoreCipher.FIELD_CIPHER_VERSION to CoreCipher.CIPHER_VERSION,
            CoreCipher.FIELD_KEY_MODEL to KEY_MODEL_KEYSTORE,
        )
        val plain = FirebaseUtil.toPlainByteDoc(fetched)
        assertArrayEquals(byteArrayOf(9, 8, 7), plain[CoreCipher.FIELD_PAYLOAD] as ByteArray)
        assertEquals(KEY_MODEL_KEYSTORE, plain[CoreCipher.FIELD_KEY_MODEL])
        assertEquals(settingsFixture(), plain["appSettings"])
        // Write direction: the cipher hands the SDK plain arrays; the
        // funnel wraps them (raw arrays are rejected by set()).
        val forWrite = FirebaseUtil.toSdkByteDoc(
            hashMapOf(
                CoreCipher.FIELD_PAYLOAD to byteArrayOf(9, 8, 7),
                CoreCipher.FIELD_CIPHER_VERSION to CoreCipher.CIPHER_VERSION,
            ),
        )
        val written = forWrite[CoreCipher.FIELD_PAYLOAD] as? Blob
            ?: error("expected the payload to be wrapped as a Blob")
        assertArrayEquals(byteArrayOf(9, 8, 7), written.toBytes())
        assertEquals(CoreCipher.CIPHER_VERSION, forWrite[CoreCipher.FIELD_CIPHER_VERSION])
    }
}
