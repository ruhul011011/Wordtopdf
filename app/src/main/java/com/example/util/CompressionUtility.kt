package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object CompressionUtility {
    fun compressImage(context: Context, imageUri: Uri, outputFile: File, quality: Int): Long {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return 0L
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return 0L
            inputStream.close()

            val fos = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.close()

            outputFile.length()
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }
}
