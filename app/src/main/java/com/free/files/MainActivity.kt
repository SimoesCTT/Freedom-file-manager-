package com.free.files

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.free.files.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileAdapter
    private var currentDir: File = File("/")
    private var atRoot = true
    private val sel = linkedSetOf<FileItem>()
    private var showHidden = false
    private var clip: Pair<File, Boolean>? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (atRoot) showRoots() else refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { goUp() }
        adapter = FileAdapter(
            mutableListOf(),
            onClick = { item -> handleClick(item) },
            onLongClick = { item -> toggleSel(item) },
            isSelected = { sel.contains(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.fabNewFolder.setOnClickListener { newFolder() }
        ensurePermissions()
        showRoots()
    }

    private fun showRoots() {
        atRoot = true
        val roots = mutableListOf<FileItem>()
        val internal = Environment.getExternalStorageDirectory()
        if (internal != null && internal.exists()) {
            roots.add(FileItem(internal, "Internal storage", true, 0L, internal.lastModified()))
        }
        try {
            val sm = getSystemService(Context.STORAGE_SERVICE) as StorageManager
            for (v in sm.storageVolumes) {
                val d = v.directory ?: continue
                if (!d.exists() || d.absolutePath == internal?.absolutePath) continue
                val desc = try { v.getDescription(this) } catch (_: Exception) { d.name }
                val label = (if (v.isRemovable) "SD/USB: " else "") + desc + "\n" + d.absolutePath
                roots.add(FileItem(d, label, true, 0L, d.lastModified()))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Vol error: ${e.message}", Toast.LENGTH_LONG).show()
        }
        adapter.update(roots)
        binding.pathText.text = "Select storage"
        binding.emptyText.visibility = if (roots.isEmpty()) View.VISIBLE else View.GONE
        binding.fabNewFolder.visibility = View.GONE
        sel.clear()
        supportActionBar?.title = "FreeFiles"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        invalidateOptionsMenu()
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            val p = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                p.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                p.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (p.isNotEmpty()) permLauncher.launch(p.toTypedArray())
        }
    }

    private fun refresh() {
        atRoot = false
        val items = FileUtils.listFiles(currentDir, showHidden)
        adapter.update(items)
        binding.pathText.text = currentDir.absolutePath
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.fabNewFolder.visibility = View.VISIBLE
        sel.clear()
        supportActionBar?.title = "FreeFiles"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        invalidateOptionsMenu()
    }

    private fun handleClick(item: FileItem) {
        if (sel.isNotEmpty()) { toggleSel(item); return }
        if (item.isDir) {
            if (item.file.canRead()) { currentDir = item.file; refresh() }
            else Toast.makeText(this, "Can't read", Toast.LENGTH_SHORT).show()
        } else if (ZipUtils.isZip(item.name)) {
            AlertDialog.Builder(this).setTitle(item.name)
                .setPositiveButton("Extract here") { _, _ -> extractZip(item.file) }
                .setNeutralButton("Open as file") { _, _ -> openFile(item.file) }
                .setNegativeButton("Cancel", null).show()
        } else openFile(item.file)
    }

    private fun toggleSel(item: FileItem) {
        if (sel.contains(item)) sel.remove(item) else sel.add(item)
        adapter.notifyDataSetChanged()
        supportActionBar?.title = if (sel.isEmpty()) "FreeFiles" else "${sel.size} selected"
        invalidateOptionsMenu()
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val mime = contentResolver.getType(uri) ?: guessMime(file.name)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun guessMime(name: String): String {
        val e = name.substringAfterLast('.', "").lowercase()
        return when (e) {
            "jpg","jpeg","png","gif","webp","bmp","heic" -> "image/*"
            "mp4","mkv","avi","mov","webm","3gp" -> "video/*"
            "mp3","m4a","ogg","wav","flac","opus" -> "audio/*"
            "pdf" -> "application/pdf"
            "txt","md","log","json","xml" -> "text/plain"
            "zip","rar","7z","tar","gz" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }

    private fun goUp() {
        if (sel.isNotEmpty()) { refresh(); return }
        if (atRoot) return
        val p = currentDir.parentFile
        if (p != null && p.canRead() && p.absolutePath != "/storage") { currentDir = p; refresh() }
        else showRoots()
    }

    private fun newFolder() {
        val input = EditText(this)
        AlertDialog.Builder(this).setTitle("New folder").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    if (File(currentDir, n).mkdir()) { Toast.makeText(this, "Created", Toast.LENGTH_SHORT).show(); refresh() }
                    else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun rename(item: FileItem) {
        val input = EditText(this).apply { setText(item.name) }
        AlertDialog.Builder(this).setTitle("Rename").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    if (item.file.renameTo(File(currentDir, n))) refresh()
                    else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun delete() {
        val items = sel.toList()
        AlertDialog.Builder(this).setTitle("Delete ${items.size}?")
            .setPositiveButton("Delete") { _, _ ->
                items.forEach { FileUtils.deleteRecursive(it.file) }
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show(); refresh()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun copy(cut: Boolean) {
        if (sel.size != 1) { Toast.makeText(this, "Select one", Toast.LENGTH_SHORT).show(); return }
        clip = sel.first().file to cut
        Toast.makeText(this, if (cut) "Cut" else "Copied", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun paste() {
        val (src, cut) = clip ?: return
        val dst = File(currentDir, src.name)
        if (dst.exists()) { Toast.makeText(this, "Exists", Toast.LENGTH_SHORT).show(); return }
        val ok = if (cut) src.renameTo(dst) else FileUtils.copyRecursive(src, dst)
        if (ok) { Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show(); clip = null; refresh() }
        else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
    }

    private fun details(item: FileItem) {
        val f = item.file
        val m = "Path: ${f.absolutePath}\nType: ${if (f.isDirectory) "Folder" else "File"}\n" +
                "Size: ${if (f.isFile) FileUtils.formatSize(f.length()) else "-"}\n" +
                "Modified: ${FileUtils.formatDate(f.lastModified())}\n" +
                "Readable: ${f.canRead()}\nWritable: ${f.canWrite()}"
        AlertDialog.Builder(this).setTitle(f.name).setMessage(m).setPositiveButton("OK", null).show()
    }

    private fun extractZip(z: File) {
        val dst = File(currentDir, z.nameWithoutExtension)
        if (dst.exists()) { Toast.makeText(this, "Exists", Toast.LENGTH_SHORT).show(); return }
        val c = ZipUtils.extract(z, dst)
        if (c >= 0) { Toast.makeText(this, "Extracted $c files", Toast.LENGTH_SHORT).show(); refresh() }
        else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
    }

    private fun compress() {
        if (sel.isEmpty()) return
        val base = if (sel.size == 1) sel.first().file.nameWithoutExtension else "archive"
        val dst = File(currentDir, "$base.zip")
        if (dst.exists()) { Toast.makeText(this, "Exists", Toast.LENGTH_SHORT).show(); return }
        if (ZipUtils.create(sel.map { it.file }, dst)) { Toast.makeText(this, "Created", Toast.LENGTH_SHORT).show(); refresh() }
        else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        if (atRoot) { menu.add(0, 2, 0, "Refresh"); return true }
        if (sel.isEmpty()) {
            menu.add(0, 1, 0, "New folder")
            menu.add(0, 2, 1, "Refresh")
            menu.add(0, 3, 2, if (showHidden) "Hide hidden" else "Show hidden")
            if (clip != null) menu.add(0, 4, 3, "Paste here")
        } else if (sel.size == 1) {
            val o = sel.first()
            menu.add(0, 10, 0, "Rename")
            menu.add(0, 11, 1, "Delete")
            menu.add(0, 12, 2, "Copy")
            menu.add(0, 13, 3, "Move")
            menu.add(0, 14, 4, "Details")
            if (o.isDir || !ZipUtils.isZip(o.name)) menu.add(0, 17, 5, "Compress")
            if (!o.isDir && ZipUtils.isZip(o.name)) menu.add(0, 16, 5, "Extract")
            menu.add(0, 15, 6, "Deselect")
        } else {
            menu.add(0, 11, 0, "Delete ${sel.size}")
            menu.add(0, 17, 1, "Compress ${sel.size}")
            menu.add(0, 15, 2, "Deselect")
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val f = sel.firstOrNull()
        return when (item.itemId) {
            1 -> { newFolder(); true }
            2 -> { if (atRoot) showRoots() else refresh(); true }
            3 -> { showHidden = !showHidden; refresh(); true }
            4 -> { paste(); true }
            10 -> { if (f != null) rename(f); true }
            11 -> { delete(); true }
            12 -> { copy(false); true }
            13 -> { copy(true); true }
            14 -> { if (f != null) details(f); true }
            15 -> { refresh(); true }
            16 -> { if (f != null) extractZip(f.file); true }
            17 -> { compress(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (sel.isNotEmpty()) { refresh(); return }
        if (atRoot) { @Suppress("DEPRECATION") super.onBackPressed(); return }
        val p = currentDir.parentFile
        if (p != null && p.canRead() && p.absolutePath != "/storage") { currentDir = p; refresh() }
        else showRoots()
    }
}
