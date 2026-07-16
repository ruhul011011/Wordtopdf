package com.example.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtility {
    fun encryptFile(inputFile: File, outputFile: File, password: String): Boolean {
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

            val fileInput = FileInputStream(inputFile)
            val inputBytes = fileInput.readBytes()
            fileInput.close()

            val encryptedBytes = cipher.doFinal(inputBytes)

            val fileOutput = FileOutputStream(outputFile)
            fileOutput.write(iv)
            fileOutput.write(encryptedBytes)
            fileOutput.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun decryptFile(inputFile: File, outputFile: File, password: String): Boolean {
        return try {
            val fileInput = FileInputStream(inputFile)
            val allBytes = fileInput.readBytes()
            fileInput.close()

            if (allBytes.size < 16) return false

            val iv = allBytes.sliceArray(0 until 16)
            val encryptedBytes = allBytes.sliceArray(16 until allBytes.size)

            val keyBytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)

            val fileOutput = FileOutputStream(outputFile)
            fileOutput.write(decryptedBytes)
            fileOutput.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
