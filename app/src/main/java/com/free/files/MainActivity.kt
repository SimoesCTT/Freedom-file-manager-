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
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.free.files.databinding.ActivityMainBinding
import android.view.View
import java.io.File
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FileAdapter
    private var currentDir: File = File("/")
    private val storagePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { goUp() }
        adapter = FileAdapter(mutableListOf()) { item ->
            if (item.isDir) { currentDir = item.file; refresh() } else openFile(item.file)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.fabNewFolder.setOnClickListener { promptNewFolder() }
        ensurePermissions()
        currentDir = FileUtils.startDir(this)
        refresh()
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
            val perms = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (perms.isNotEmpty()) storagePermLauncher.launch(perms.toTypedArray())
        }
    }
    private fun refresh() {
        val items = FileUtils.listFiles(currentDir)
        adapter.update(items)
        binding.pathText.text = currentDir.absolutePath
        binding.emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
    private fun goUp() {
        val parent = currentDir.parentFile
        if (parent != null && parent.canRead()) { currentDir = parent; refresh() }
        else Toast.makeText(this, "Already at top", Toast.LENGTH_SHORT).show()
    }
    private fun openFile(file: File) {
        try {
            val uri = Uri.fromFile(file)
            val mime = contentResolver.getType(uri) ?: "*/*"
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open with"))
        } catch (_: Exception) {
            Toast.makeText(this, "No app to open this file", Toast.LENGTH_SHORT).show()
        }
    }
    private fun promptNewFolder() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_new_folder)
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (File(currentDir, name).mkdir()) { Toast.makeText(this, "Created", Toast.LENGTH_SHORT).show(); refresh() }
                    else Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean { menu.add(0, 1, 0, R.string.menu_refresh); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == 1) { refresh(); true } else super.onOptionsItemSelected(item)
}
