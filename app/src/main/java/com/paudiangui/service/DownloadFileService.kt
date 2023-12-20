package com.paudiangui.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DownloadFileService {

    suspend fun downloadFile(context: Context, fileUrl: String): String? {
        val client = OkHttpClient()

        val request = Request.Builder().url(fileUrl).build()


        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val inputStream: InputStream? = response.body()?.byteStream()
                    val fileName = fileUrl.substringAfterLast("/")
                    val apkStorage = File(context.filesDir.toString() + "/" + MEDIA_FOLDER + "/")

                    val outputFile =
                        File(apkStorage, fileName)
                    Log.d(TAG, "File: ${outputFile.path}")
                    val parentDir = outputFile.parentFile
                    if (!parentDir.exists()) {
                        parentDir.mkdirs()
                        Log.i(TAG, "Parent File Created")
                    }

                    //Create New File if not present
                    if (!outputFile.exists()) {
                        outputFile.createNewFile()
                        Log.i(TAG, "File Created")
                    } else {
                        // file corrupted or damaged
                        if (!outputFile.canRead()) {
                            if (outputFile.delete()) {
                                outputFile.createNewFile()
                                Log.i(TAG, "File damaged, creating new one")
                            }
                        } else {
                            Log.w(TAG, "File already exists, aborting download")
                            return@withContext outputFile.absolutePath
                        }
                    }

                    inputStream?.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(4 * 1024)
                            var byteCount: Int
                            while (input.read(buffer).also { byteCount = it } != -1) {
                                output.write(buffer, 0, byteCount)
                            }
                            output.flush()
                            Log.i(TAG, "File downloaded successfully")
                            return@withContext outputFile.absolutePath
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, e.message.toString())
            }
            return@withContext null
        }
    }

    companion object {
        private val TAG = DownloadFileService::class.java.simpleName
        const val MEDIA_FOLDER = "paudiangui"
    }
}