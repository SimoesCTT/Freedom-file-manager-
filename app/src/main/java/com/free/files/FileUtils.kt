package com.free.files

import android.os.Environment
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun listFiles(dir: File, showHidden: Boolean): List<FileItem> {
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { showHidden || !it.name.startsWith(".") }
            .map {
                FileItem(
                    file = it,
                    name = it.name,
                    isDir = it.isDirectory,
                    size = if (it.isFile) it.length() else 0L,
                    lastModified = it.lastModified()
                )
            }
            .sortedWith(
                compareByDescending<FileItem> { it.isDir }
                    .thenBy { it.name.lowercase() }
            )
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

    fun startDir(): File =
        Environment.getExternalStorageDirectory() ?: File("/")

    fun deleteRecursive(f: File): Boolean {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursive(it) }
        return f.delete()
    }

    fun copyRecursive(src: File, dst: File): Boolean {
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) return false
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name)) }
            return true
        }
        return try {
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) { false }
    }
}
