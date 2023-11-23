package at.msd.friehs_bicha.cdcsvparser.util

import android.content.Context
import android.net.Uri
import at.msd.friehs_bicha.cdcsvparser.logging.FileLog
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

object FileUtil {

    @Throws(IOException::class)
    fun getFileContentFromPath(context: Context, filePath: String): List<String> {
        try {
            val inputStream: InputStream = context.openFileInput(filePath)
            return readInputStreamAsList(inputStream)
        } catch (e: IOException) {
            // Handle IOException, e.g., log or notify the user
            FileLog.e("FileUtil", "Error reading file content from $filePath with exception: $e")
            throw IOException("Error reading file content from $filePath", e)
        }
    }

    @Throws(IOException::class)
    fun getFileContent(file: File): ArrayList<String> {
        try {
            val inputStream: InputStream = file.inputStream()
            return readInputStreamAsList(inputStream).toMutableList() as ArrayList<String>
        } catch (e: IOException) {
            // Handle IOException, e.g., log or notify the user
            FileLog.e(
                "FileUtil",
                "Error reading file content from ${file.absolutePath} with exception: $e"
            )
            throw IOException("Error reading file content from ${file.absolutePath}", e)
        }
    }

    @Throws(IOException::class)
    fun getFileContentFromUri(context: Context, uri: Uri): ArrayList<String> {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                return readInputStreamAsList(inputStream).toMutableList() as ArrayList<String>
            } else {
                throw IOException("InputStream is null for Uri: $uri")
            }
        } catch (e: IOException) {
            // Handle IOException, e.g., log or notify the user
            FileLog.e("FileUtil", "Error reading file content from Uri: $uri with exception: $e")
            throw IOException("Error reading file content from Uri: $uri", e)
        }
    }

    @Throws(IOException::class)
    private fun readInputStreamAsList(inputStream: InputStream): MutableList<String> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = mutableListOf<String>()
        var line: String? = reader.readLine()
        while (line != null) {
            lines.add(line)
            line = reader.readLine()
        }
        reader.close()
        return lines
    }
}
