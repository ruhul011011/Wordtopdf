package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

object DocxUtility {
    fun extractTextFromDocx(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val zipInputStream = ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry
            var documentXmlStream: InputStream? = null
            
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    documentXmlStream = zipInputStream
                    break
                }
                entry = zipInputStream.nextEntry
            }
            
            if (documentXmlStream == null) {
                zipInputStream.close()
                return null
            }
            
            val textBuilder = StringBuilder()
            val parser = Xml.newPullParser()
            parser.setInput(documentXmlStream, "UTF-8")
            
            var eventType = parser.eventType
            var inParagraph = false
            var currentTag: String? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = name
                        if (name == "w:p" || name == "p") {
                            inParagraph = true
                        } else if (name == "w:tab" || name == "tab") {
                            textBuilder.append("\t")
                        } else if (name == "w:br" || name == "br" || name == "w:cr" || name == "cr") {
                            textBuilder.append("\n")
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (currentTag == "w:t" || currentTag == "t") {
                            val text = parser.text
                            if (text != null) {
                                textBuilder.append(text)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = null
                        if (name == "w:p" || name == "p") {
                            textBuilder.append("\n")
                            inParagraph = false
                        }
                    }
                }
                eventType = parser.next()
            }
            
            zipInputStream.close()
            textBuilder.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
