package com.free.files

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
    private val selection = linkedSetOf<FileItem>()
    private var showHidden = false
    private var clipboard: Pair<File, Boolean>? = null // (source, cut?)

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { goUp() }

        adapter = FileAdapter(
            mutableListOf(),
            onClick = { item -> handleClick(item) },
            onLongClick = { item -> toggleSelection(item) },
            isSelected = { selection.contains(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.fabNewFolder.setOnClickListener { promptNewFolder() }

        ensurePermissions()
        currentDir = FileUtils.startDir()
        refresh()
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            val perms = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (perms.isNotEmpty()) permLauncher.launch(perms.toTypedArray())
        }
    }

    private fun refresh() {
        val items = FileUtils.listFiles(currentDir, showHidden)
        adapter.update(items)
        binding.pathText.text = currentDir.absolutePath
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        selection.clear()
        supportActionBar?.title = "FreeFiles"
        invalidateOptionsMenu()
    }

    private fun handleClick(item: FileItem) {
        if (selection.isNotEmpty()) { toggleSelection(item); return }
        if (item.isDir) {
            if (item.file.canRead()) { currentDir = item.file; refresh() }
            else Toast.makeText(this, "Can't read folder", Toast.LENGTH_SHORT).show()
        } else openFile(item.file)
    }

    private fun toggleSelection(item: FileItem) {
        if (selection.contains(item)) selection.remove(item) else selection.add(item)
        adapter.notifyDataSetChanged()
        supportActionBar?.title =
            if (selection.isEmpty()) "FreeFiles" else "${selection.size} selected"
        invalidateOptionsMenu()
    }

    /** THE FIX: use FileProvider, not Uri.fromFile() */
    private fun openFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val mime = contentResolver.getType(uri) ?: guessMime(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(this, "Can't open: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
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
        if (selection.isNotEmpty()) { refresh(); return }
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead()) { currentDir = parent; refresh() }
        else Toast.makeText(this, "Already at top", Toast.LENGTH_SHORT).show()
    }

    private fun promptNewFolder() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("New folder").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (File(currentDir, name).mkdir()) {
                        Toast.makeText(this, "Created", Toast.LENGTH_SHORT).show()
                        refresh()
                    } else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun promptRename(item: FileItem) {
        val input = EditText(this).apply { setText(item.name) }
        AlertDialog.Builder(this)
            .setTitle("Rename").setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (item.file.renameTo(File(currentDir, newName))) refresh()
                    else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmDelete() {
        val items = selection.toList()
        AlertDialog.Builder(this)
            .setTitle("Delete ${items.size} item(s)?")
            .setMessage("Cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                items.forEach { FileUtils.deleteRecursive(it.file) }
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun copySelection(cut: Boolean) {
        if (selection.size != 1) {
            Toast.makeText(this, "Select one item", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard = selection.first().file to cut
        Toast.makeText(this, if (cut) "Cut" else "Copied", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun pasteHere() {
        val (src, cut) = clipboard ?: return
        val dst = File(currentDir, src.name)
        if (dst.exists()) {
            Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = if (cut) src.renameTo(dst) else FileUtils.copyRecursive(src, dst)
        if (ok) {
            Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
            clipboard = null
            refresh()
        } else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
    }

    private fun showDetails(item: FileItem) {
        val f = item.file
        val msg = "Name: ${f.name}\n" +
                  "Path: ${f.absolutePath}\n" +
                  "Type: ${if (f.isDirectory) "Folder" else "File"}\n" +
                  "Size: ${if (f.isFile) FileUtils.formatSize(f.length()) else "-"}\n" +
                  "Modified: ${FileUtils.formatDate(f.lastModified())}\n" +
                  "Readable: ${f.canRead()}\n" +
                  "Writable: ${f.canWrite()}"
        AlertDialog.Builder(this).setTitle(f.name).setMessage(msg)
            .setPositiveButton("OK", null).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        if (selection.isEmpty()) {
            menu.add(0, 1, 0, "New folder")
            menu.add(0, 2, 1, "Refresh")
            menu.add(0, 3, 2, if (showHidden) "Hide hidden" else "Show hidden")
            if (clipboard != null) menu.add(0, 4, 3, "Paste here")
        } else if (selection.size == 1) {
            menu.add(0, 10, 0, "Rename")
            menu.add(0, 11, 1, "Delete")
            menu.add(0, 12, 2, "Copy")
            menu.add(0, 13, 3, "Move")
            menu.add(0, 14, 4, "Details")
            menu.add(0, 15, 5, "Deselect")
        } else {
            menu.add(0, 11, 0, "Delete ${selection.size} items")
            menu.add(0, 15, 1, "Deselect")
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val first = selection.firstOrNull()
        return when (item.itemId) {
            1 -> { promptNewFolder(); true }
            2 -> { refresh(); true }
            3 -> { showHidden = !showHidden; refresh(); true }
            4 -> { pasteHere(); true }
            10 -> { if (first != null) promptRename(first); true }
            11 -> { confirmDelete(); true }
            12 -> { copySelection(false); true }
            13 -> { copySelection(true); true }
            14 -> { if (first != null) showDetails(first); true }
            15 -> { refresh(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selection.isNotEmpty()) { refresh(); return }
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead()) { currentDir = parent; refresh() }
        else @Suppress("DEPRECATION") super.onBackPressed()
    }
}
