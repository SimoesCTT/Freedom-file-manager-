package com.free.files
import java.io.File
data class FileItem(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long
)
