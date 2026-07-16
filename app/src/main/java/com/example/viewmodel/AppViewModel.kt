package com.example.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import com.example.util.CompressionUtility
import com.example.util.CryptoUtility
import com.example.util.DocxUtility
import com.example.util.GeminiUtility
import com.example.util.PdfUtility
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed class Screen {
    object Dashboard : Screen()
    object Convert : Screen()
    object Editor : Screen()
    object Merge : Screen()
    object Optimize : Screen()
    object AiSummary : Screen()
    object Security : Screen()
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Processing : ProcessingState()
    data class Success(val message: String, val savedUri: Uri? = null, val extraDetail: String? = null) : ProcessingState()
    data class Error(val error: String) : ProcessingState()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val historyDao = AppDatabase.getDatabase(application).historyDao()
    private val repository = HistoryRepository(historyDao)

    val historyList: StateFlow<List<HistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    var currentScreen by mutableStateOf<Screen>(Screen.Dashboard)
    var commandInput by mutableStateOf("")

    var processingState by mutableStateOf<ProcessingState>(ProcessingState.Idle)

    // Helper to format bytes cleanly
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    // Convert Image to PDF
    fun convertImageToPdf(context: Context, uri: Uri, customName: String) {
        viewModelScope.launch {
            processingState = ProcessingState.Processing
            val startTime = System.currentTimeMillis()
            val fileName = if (customName.endsWith(".pdf")) customName else "$customName.pdf"
            val cacheFile = File(context.cacheDir, fileName)

            val success = PdfUtility.convertImageToPdf(context, uri, cacheFile)
            if (success && cacheFile.exists()) {
                val finalUri = saveFileToDownloads(context, cacheFile, "application/pdf")
                val duration = System.currentTimeMillis() - startTime
                val fileSizeStr = formatBytes(cacheFile.length())

                repository.insert(
                    HistoryEntity(
                        filename = fileName,
                        toolName = "Image to PDF",
                        fileSize = fileSizeStr,
                        timestamp = System.currentTimeMillis(),
                        fileUriString = finalUri?.toString()
                    )
                )

                processingState = ProcessingState.Success(
                    message = "Image successfully converted to PDF!",
                    savedUri = finalUri,
                    extraDetail = "Size: $fileSizeStr | Time: ${duration}ms"
                )
            } else {
                processingState = ProcessingState.Error("Failed to convert image to PDF.")
            }
        }
    }

    // Convert Text to PDF
    fun convertTextToPdf(context: Context, text: String, title: String, customName: String) {
        viewModelScope.launch {
            processingState = ProcessingState.Processing
            val startTime = System.currentTimeMillis()
            val fileName = if (customName.endsWith(".pdf")) customName else "$customName.pdf"
            val cacheFile = File(context.cacheDir, fileName)

            val success = PdfUtility.convertTextToPdf(text, title, cacheFile)
            if (success && cacheFile.exists()) {
                val finalUri = saveFileToDownloads(context, cacheFile, "application/pdf")
                val duration = System.currentTimeMillis() - startTime
                val fileSizeStr = formatBytes(cacheFile.length())

                repository.insert(
                    HistoryEntity(
                        filename = fileName,
                        toolName = "Text to PDF",
                        fileSize = fileSizeStr,
                        timestamp = System.currentTimeMillis(),
                        fileUriString = finalUri?.toString()
                    )
                )

                processingState = ProcessingState.Success(
                    message = "Text successfully converted to PDF!",
                    savedUri = finalUri,
                    extraDetail = "Size: $fileSizeStr | Time: ${duration}ms"
                )
            } else {
                processingState = ProcessingState.Error("Failed to convert text to PDF.")
            }
        }
    }

    // Convert DOCX to PDF
    fun convertDocxToPdf(context: Context, docxUri: Uri, customName: String) {
        viewModelScope.launch {
            processingState = ProcessingState.Processing
            val startTime = System.currentTimeMillis()
            val fileName = if (customName.endsWith(".pdf")) customName else "$customName.pdf"
            val cacheFile = File(context.cacheDir, fileName)

            // Extract text content from Word Document locally
            val extractedText = DocxUtility.extractTextFromDocx(context, docxUri)
            if (extractedText == null) {
                processingState = ProcessingState.Error("Failed to extract text from DOCX file. Is it a valid .docx document?")
                return@launch
            }

            if (extractedText.isBlank()) {
                processingState = ProcessingState.Error("The selected .docx file does not contain any readable text.")
                return@launch
            }

            // Convert the extracted text to PDF
            val success = PdfUtility.convertTextToPdf(extractedText, customName.substringBeforeLast(".docx"), cacheFile)
            if (success && cacheFile.exists()) {
                val finalUri = saveFileToDownloads(context, cacheFile, "application/pdf")
                val duration = System.currentTimeMillis() - startTime
                val fileSizeStr = formatBytes(cacheFile.length())

                repository.insert(
                    HistoryEntity(
                        filename = fileName,
                        toolName = "DOCX to PDF",
                        fileSize = fileSizeStr,
                        timestamp = System.currentTimeMillis(),
                        fileUriString = finalUri?.toString()
                    )
                )

                processingState = ProcessingState.Success(
                    message = "DOCX successfully converted to PDF!",
                    savedUri = finalUri,
                    extraDetail = "Size: $fileSizeStr | Time: ${duration}ms"
                )
            } else {
                processingState = ProcessingState.Error("Failed to generate PDF from DOCX.")
            }
        }
    }

    // Compress Image
    fun compressImage(context: Context, uri: Uri, customName: String, quality: Int) {
        viewModelScope.launch {
            processingState = ProcessingState.Processing
            val startTime = System.currentTimeMillis()
            val fileName = if (customName.contains(".")) customName else "$customName.jpg"
            val cacheFile = File(context.cacheDir, fileName)

            val originalSize = getUriSize(context, uri)
            val newSize = CompressionUtility.compressImage(context, uri, cacheFile, quality)

            if (newSize > 0L && cacheFile.exists()) {
                val finalUri = saveFileToDownloads(context, cacheFile, "image/jpeg")
                val duration = System.currentTimeMillis() - startTime
                val origStr = formatBytes(originalSize)
                val newStr = formatBytes(newSize)
                val reduction = ((originalSize - newSize).toFloat() / originalSize.toFloat() * 100).toInt().coerceIn(0, 100)

                repository.insert(
                    HistoryEntity(
                        filename = fileName,
                        toolName = "Optimize / Compress",
                        fileSize = newStr,
                        timestamp = System.currentTimeMillis(),
                        fileUriString = finalUri?.toString()
                    )
                )

                processingState = ProcessingState.Success(
                    message = "Image successfully compressed by $reduction%!",
                    savedUri = finalUri,
                    extraDetail = "Original: $origStr -> Compressed: $newStr | Saved: $reduction% | Time: ${duration}ms"
                )
            } else {
                processingState = ProcessingState.Error("Failed to compress image.")
            }
        }
    }

    // Encrypt file or text
    fun encryptTextFile(context: Context, text: String, customName: String, password: String) {
        viewModelScope.launch {
            processingState = ProcessingState.Processing
            val startTime = System.currentTimeMillis()
            val rawFileName = if (customName.contains(".")) customName else "$customName.txt"
            val encFileName = "$rawFileName.enc"

            val tempFile = File(context.cacheDir, rawFileName)
            tempFile.writeText(text, Charsets.UTF_8)

            val cacheFile = File(context.cacheDir, encFileName)
            val success = CryptoUtility.encryptFile(tempFile, cacheFile, password)

            tempFile.delete() // clean up raw text file

            if (success && cacheFile.exists()) {
                val finalUri = saveFileToDownloads(context, cacheFile, "application/octet-stream")
                val duration = System.currentTimeMillis() - startTime
                val fileSizeStr = formatBytes(cacheFile.length())

                repository.insert(
                    HistoryEntity(
                        filename = encFileName,
                        toolName = "Security (Encrypt)",
                        fileSize = fileSizeStr,
                        timestamp = System.currentTimeMillis(),
                        fileUriString = finalUri?.toString()
                    )
                )

                processingState = ProcessingState.Success(
                    message = "File encrypted with AES-256 and saved!",
                    savedUri = finalUri,
                    extraDetail = "Encrypted Size: $fileSizeStr | Time: ${duration}ms"
                )
            } else {
                processingState = ProcessingState.Error("Encryption failed.")
            }
        }
    }

    // Decrypt file or text from URI
    fun decryptSelectedFile(context: Context, uri: Uri, password: String) {
        viewModelScope.launch {
            processingState = ProcessingState.Processing
            val startTime = System.currentTimeMillis()
            val tempInFile = File(context.cacheDir, "temp_encrypted.enc")
            val tempOutFile = File(context.cacheDir, "decrypted_text.txt")

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempInFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val success = CryptoUtility.decryptFile(tempInFile, tempOutFile, password)
                tempInFile.delete()

                if (success && tempOutFile.exists()) {
                    val decryptedText = tempOutFile.readText(Charsets.UTF_8)
                    tempOutFile.delete()
                    val duration = System.currentTimeMillis() - startTime

                    processingState = ProcessingState.Success(
                        message = "File decrypted successfully!",
                        extraDetail = "Content Decrypted: \n\n$decryptedText \n\n(Time taken: ${duration}ms)"
                    )
                } else {
                    processingState = ProcessingState.Error("Decryption failed. Please check your password.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                processingState = ProcessingState.Error("Error decrypting file: ${e.localizedMessage}")
            }
        }
    }

    // AI Summarize Text
    fun generateAiSummary(context: Context, text: String, docName: String) {
        viewModelScope.launch {
            processingState = ProcessingState.Processing
            val startTime = System.currentTimeMillis()

            val summaryResult = GeminiUtility.generateSummary(text)
            val duration = System.currentTimeMillis() - startTime

            // Save summary text locally as a report
            val summaryFileName = "${docName}_summary.txt"
            val cacheFile = File(context.cacheDir, summaryFileName)
            cacheFile.writeText(summaryResult, Charsets.UTF_8)

            val finalUri = saveFileToDownloads(context, cacheFile, "text/plain")

            repository.insert(
                HistoryEntity(
                    filename = summaryFileName,
                    toolName = "AI Summary",
                    fileSize = formatBytes(cacheFile.length()),
                    timestamp = System.currentTimeMillis(),
                    fileUriString = finalUri?.toString()
                )
            )

            processingState = ProcessingState.Success(
                message = "AI Summary Generated Successfully!",
                savedUri = finalUri,
                extraDetail = summaryResult
            )
        }
    }

    // Save annotated drawing
    fun saveDrawing(context: Context, bitmapBytes: ByteArray, customName: String) {
        viewModelScope.launch {
            processingState = ProcessingState.Processing
            val startTime = System.currentTimeMillis()
            val fileName = if (customName.endsWith(".jpg")) customName else "$customName.jpg"
            val cacheFile = File(context.cacheDir, fileName)

            try {
                cacheFile.writeBytes(bitmapBytes)
                val finalUri = saveFileToDownloads(context, cacheFile, "image/jpeg")
                val duration = System.currentTimeMillis() - startTime
                val fileSizeStr = formatBytes(cacheFile.length())

                repository.insert(
                    HistoryEntity(
                        filename = fileName,
                        toolName = "PDF Annotator",
                        fileSize = fileSizeStr,
                        timestamp = System.currentTimeMillis(),
                        fileUriString = finalUri?.toString()
                    )
                )

                processingState = ProcessingState.Success(
                    message = "Drawing saved successfully!",
                    savedUri = finalUri,
                    extraDetail = "Size: $fileSizeStr | Time: ${duration}ms"
                )
            } catch (e: Exception) {
                e.printStackTrace()
                processingState = ProcessingState.Error("Failed to save drawing: ${e.localizedMessage}")
            }
        }
    }

    // Clear history
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // Delete history item
    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    // Media Store Helper
    @android.annotation.SuppressLint("NewApi")
    private fun saveFileToDownloads(context: Context, file: File, mimeType: String): Uri? {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WordToPDFHub")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                uri
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "WordToPDFHub")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }
                val destFile = File(appDir, file.name)
                file.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(destFile)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun getUriSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
