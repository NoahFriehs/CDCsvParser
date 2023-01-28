package at.msd.friehs_bicha.cdcsvparser.encryption;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class AESEncryption {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";


    /**
     * Decrypts the given files using the specified key.
     *
     * @param key The key to use for decryption.
     * @param inputContent The list of lines to decrypt.
     * @return The decrypted file.
     * @throws Exception if any error occurs during the decryption process.
     */
    public static ArrayList<String> decrypt(String key, ArrayList<String> inputContent) throws Exception {
        // Create a new byte array output stream to store the decrypted data
        ByteArrayOutputStream decryptedData = new ByteArrayOutputStream();

        Base64.Decoder decoder = Base64.getDecoder();
        byte[] decodedKey = decoder.decode(key);
        SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, ALGORITHM);

        // Iterate over the input files
        for (String inputFile : inputContent) {
            // Create a new byte array input stream to read the encrypted content
            ByteArrayInputStream inputStream = new ByteArrayInputStream(inputFile.getBytes());

            // Initialize the cipher for decryption
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            // Create a new cipher input stream to read the encrypted data
            CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);

            // Read the decrypted data and write it to the output stream
            byte[] buffer = new byte[1024];
            int bytesRead;
            try {
                while ((bytesRead = cipherInputStream.read(buffer)) != -1) {
                    decryptedData.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Close the input stream
            inputStream.close();
        }

        // Convert the decrypted data to a string and split it into an array of lines
        String[] lines = decryptedData.toString().split("\n");
        ArrayList<String> decryptedLines = new ArrayList<>(Arrays.asList(lines));
        return decryptedLines;
    }



    /**
     * Encrypts the given files using the specified key.
     *
     * @param key The key to use for encryption.
     * @param inputFile The list of files to encrypt.
     * @throws Exception if any error occurs during the encryption process.
     */
    public static void encrypt(String key, ArrayList<String> inputFile, FileOutputStream outputFile)
            throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IOException,
            IllegalBlockSizeException, BadPaddingException {

        Base64.Decoder decoder = Base64.getDecoder();
        byte[] decodedKey = decoder.decode(key);
        SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        StringBuilder sb = new StringBuilder();
        for (String line : inputFile) {
            sb.append(line).append("\n");
        }
        byte[] inputBytes = sb.toString().getBytes();
        byte[] outputBytes = cipher.doFinal(inputBytes);

        Base64.Encoder encoder = Base64.getEncoder();
        outputFile.write(encoder.encode(outputBytes));

        outputFile.close();
    }

}
