package com.free.files

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    fun isZip(name: String): Boolean = name.lowercase().endsWith(".zip")

    fun extract(zipFile: File, dstDir: File): Int {
        if (!dstDir.exists()) dstDir.mkdirs()
        var count = 0
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = File(dstDir, entry.name)
                    val canonical = outFile.canonicalPath
                    if (!canonical.startsWith(dstDir.canonicalPath + File.separator)
                        && canonical != dstDir.canonicalPath) {
                        zis.closeEntry(); entry = zis.nextEntry; continue
                    }
                    if (entry.isDirectory) outFile.mkdirs()
                    else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            val buf = ByteArray(8192)
                            var n: Int
                            while (zis.read(buf).also { n = it } > 0) fos.write(buf, 0, n)
                        }
                        count++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            return count
        } catch (e: Exception) { return -1 }
    }

    fun create(sources: List<File>, dstZip: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(dstZip)).use { zos ->
                sources.forEach { addToZip(it, it.name, zos) }
            }
            true
        } catch (e: Exception) { false }
    }

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val entries = file.listFiles() ?: return
            if (entries.isEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            } else entries.forEach { addToZip(it, "$entryName/${it.name}", zos) }
        } else {
            FileInputStream(file).use { fis ->
                zos.putNextEntry(ZipEntry(entryName))
                val buf = ByteArray(8192)
                var n: Int
                while (fis.read(buf).also { n = it } > 0) zos.write(buf, 0, n)
                zos.closeEntry()
            }
        }
    }
}
