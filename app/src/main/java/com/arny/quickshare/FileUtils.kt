package com.arny.quickshare

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.util.Locale

object FileUtils {
    fun getMimeType(uri: Uri, context: Context): String? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.getType(uri)
        } else {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension?.lowercase(Locale.getDefault()))
        }
    }

    fun isTextFile(mimeType: String?): Boolean {
        if (mimeType == null) return false
        
        val textMimeTypes = setOf(
            "application/x-markdown",
            "text/markdown",
            "text/x-markdown",
            "text/plain"
        )
        
        return mimeType.startsWith("text/", ignoreCase = true) || 
               textMimeTypes.contains(mimeType.lowercase(Locale.getDefault()))
    }
}