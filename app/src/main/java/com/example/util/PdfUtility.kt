package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object PdfUtility {
    fun convertImageToPdf(context: Context, imageUri: Uri, outputFile: File): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return false
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return false
            inputStream.close()

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val canvas = page.canvas
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)

            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun convertTextToPdf(text: String, title: String, outputFile: File): Boolean {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val canvas = page.canvas
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 14f
                color = Color.BLACK
            }

            // Draw header
            val headerPaint = Paint().apply {
                isAntiAlias = true
                textSize = 20f
                isFakeBoldText = true
                color = Color.rgb(11, 19, 38)
            }
            canvas.drawText(title, 40f, 60f, headerPaint)

            // Draw border line
            val linePaint = Paint().apply {
                color = Color.rgb(173, 198, 255)
                strokeWidth = 2f
            }
            canvas.drawLine(40f, 80f, 555f, 80f, linePaint)

            val x = 40f
            var y = 110f
            val maxLineWidth = 515
            val lines = text.split("\n")

            for (line in lines) {
                var remainingText = line
                if (remainingText.isEmpty()) {
                    y += 15f
                    continue
                }
                while (remainingText.isNotEmpty()) {
                    val charsCount = paint.breakText(remainingText, true, maxLineWidth.toFloat(), null)
                    if (charsCount <= 0) break
                    val lineToDraw = remainingText.substring(0, charsCount)
                    canvas.drawText(lineToDraw, x, y, paint)
                    y += 20f
                    if (y > 800) {
                        break
                    }
                    remainingText = remainingText.substring(charsCount)
                }
                y += 5f
                if (y > 800) break
            }

            pdfDocument.finishPage(page)
            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
