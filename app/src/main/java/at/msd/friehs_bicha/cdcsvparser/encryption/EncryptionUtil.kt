package at.msd.friehs_bicha.cdcsvparser.encryption
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

class EncryptionUtil {
    companion object {
        private const val RSA_ALGORITHM = "RSA"

        fun generateKeyPair(): Pair<PublicKey, PrivateKey> {
            val keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM)
            keyGen.initialize(2048)
            val keyPair = keyGen.generateKeyPair()
            return Pair(keyPair.public, keyPair.private)
        }

        fun encryptString(plaintext: String, publicKey: PublicKey): ByteArray {
            val cipher = Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            return cipher.doFinal(plaintext.toByteArray())
        }

        fun decryptString(ciphertext: ByteArray, privateKey: PrivateKey): String {
            val cipher = Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            return String(cipher.doFinal(ciphertext))
        }

        /**
         * Encrypts a HashMap<String, HashMap<String, Any>> to a ByteArray
         * WARNING: if the hashmap is too big(like TxApp big) it will throw an exception
         */
        fun encryptHashMap(
            hashMap: HashMap<String, HashMap<String, Any>>,
            publicKey: PublicKey
        ): ByteArray {
            val jsonString = hashMapToJson(hashMap)
            return encryptString(jsonString, publicKey)
        }

        fun decryptHashMap(
            ciphertext: ByteArray,
            privateKey: PrivateKey
        ): HashMap<String, HashMap<String, String>> {
            val jsonString = decryptString(ciphertext, privateKey)
            return jsonToHashMap(jsonString)
        }

        fun hashMapToJson(hashMap: HashMap<String, HashMap<String, Any>>): String {
            val gson = Gson()
            return gson.toJson(hashMap)
        }

        fun jsonToHashMap(jsonString: String): HashMap<String, HashMap<String, String>> {
            val gson = Gson()
            val type = object : TypeToken<HashMap<String, HashMap<String, String>>>() {}.type
            return gson.fromJson(jsonString, type)
        }
    }
}
