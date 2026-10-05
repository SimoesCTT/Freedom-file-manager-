package com.free.files
import android.content.Context
import android.os.Environment
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
object FileUtils {
    fun listFiles(dir: File): List<FileItem> {
        val files = dir.listFiles() ?: return emptyList()
        return files.map {
            FileItem(it, it.name, it.isDirectory, if (it.isFile) it.length() else 0L, it.lastModified())
        }.sortedWith(compareByDescending<FileItem> { it.isDir }.thenBy { it.name.lowercase() })
    }
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble(); var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return DecimalFormat("#,##0.#").format(v) + " " + units[i]
    }
    fun formatDate(millis: Long): String =
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(millis))
    fun startDir(context: Context): File {
        val ext = Environment.getExternalStorageDirectory()
        return if (ext != null && ext.exists()) ext else context.filesDir
    }
}
