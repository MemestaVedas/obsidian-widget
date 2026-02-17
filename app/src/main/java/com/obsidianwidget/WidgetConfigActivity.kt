package com.obsidianwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedVaultUri: Uri? = null
    private var vaultFiles: MutableList<VaultFileItem> = mutableListOf()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var btnSelectVault: Button
    private lateinit var tvVaultPath: TextView
    private lateinit var etVaultName: EditText
    private lateinit var lvVaultFiles: ListView
    private lateinit var btnSaveConfig: Button

    private val openDocumentTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        scope.launch {
            try {
                // Take persistent permission
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                selectedVaultUri = uri
                
                val folderName = withContext(Dispatchers.IO) {
                    val docFile = DocumentFile.fromTreeUri(this@WidgetConfigActivity, uri)
                    val name = docFile?.name ?: "Unknown"

                    // Check for .obsidian folder
                    val obsidianFolder = docFile?.listFiles()?.find { it.name == ".obsidian" && it.isDirectory }
                    
                    withContext(Dispatchers.Main) {
                        if (obsidianFolder != null) {
                            Toast.makeText(this@WidgetConfigActivity, "Valid Obsidian vault recognized!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@WidgetConfigActivity, "Warning: '.obsidian' folder not found", Toast.LENGTH_LONG).show()
                        }
                    }
                    name
                }

                tvVaultPath.text = folderName
                etVaultName.setText(folderName)

                loadVaultFiles(uri)
            } catch (e: Exception) {
                Log.e("WidgetConfig", "Error setting up vault", e)
                Toast.makeText(this@WidgetConfigActivity, "Error accessing folder: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private lateinit var etSearchNotes: EditText
    private var isStandaloneMode = false
    private var filteredFiles: List<VaultFileItem> = emptyList()
    private val selectedUris = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_widget_config)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        isStandaloneMode = intent?.getStringExtra("mode") == "standalone"

        Log.d("WidgetConfig", "Received appWidgetId: $appWidgetId, mode: ${intent?.getStringExtra("mode")}")

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !isStandaloneMode) {
            Log.e("WidgetConfig", "Invalid appWidgetId received and not in standalone mode")
            finish()
            return
        }

        initViews()
        setupListeners()
        loadExistingConfig()

        if (isStandaloneMode) {
            btnSaveConfig.text = "Update Widget Notes"
        }
    }


    private fun initViews() {
        btnSelectVault = findViewById(R.id.btn_select_vault)
        tvVaultPath = findViewById(R.id.tv_vault_path)
        etVaultName = findViewById(R.id.et_vault_name)
        lvVaultFiles = findViewById(R.id.lv_vault_files)
        btnSaveConfig = findViewById(R.id.btn_save_config)
        etSearchNotes = findViewById(R.id.et_search_notes)
    }

    private fun setupListeners() {
        btnSelectVault.setOnClickListener {
            openDocumentTree.launch(null)
        }

        btnSaveConfig.setOnClickListener {
            saveConfiguration()
        }

        etSearchNotes.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterFiles(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        lvVaultFiles.setOnItemClickListener { _, _, position, _ ->
            val file = filteredFiles[position]
            if (lvVaultFiles.isItemChecked(position)) {
                selectedUris.add(file.relativePath)
            } else {
                selectedUris.remove(file.relativePath)
            }
        }
    }

    private fun filterFiles(query: String) {
        filteredFiles = if (query.isEmpty()) {
            vaultFiles
        } else {
            val q = query.lowercase()
            val list = mutableListOf<VaultFileItem>()
            for (file in vaultFiles) {
                if (file.displayName.lowercase().contains(q)) {
                    list.add(file)
                }
            }
            list
        }
        updateAdapter()
    }

    private fun updateAdapter() {
        val fileNames = mutableListOf<String>()
        for (file in filteredFiles) {
            fileNames.add(file.displayName)
        }
        val adapter = ArrayAdapter(
            this@WidgetConfigActivity,
            android.R.layout.simple_list_item_multiple_choice,
            fileNames
        )
        lvVaultFiles.adapter = adapter
        
        // Restore pre-selections from our Set
        for (i in filteredFiles.indices) {
            val file = filteredFiles[i]
            if (selectedUris.contains(file.relativePath)) {
                lvVaultFiles.setItemChecked(i, true)
            }
        }
    }

    private fun loadExistingConfig() {
        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        val existingPath = prefs.getString("obsidian_vault_path", null)
        val existingName = prefs.getString("obsidian_vault_name", null)
        val existingUri = prefs.getString("obsidian_vault_uri", null)

        if (existingPath != null) {
            tvVaultPath.text = existingPath
        }
        if (existingName != null) {
            etVaultName.setText(existingName)
        }
        if (existingUri != null) {
            selectedVaultUri = Uri.parse(existingUri)
            loadVaultFiles(Uri.parse(existingUri))
        }

        // Initialize selectedUris from repository
        val repository = NoteRepository(this)
        repository.getAllNotes().forEach { selectedUris.add(it.obsidianUri) }
    }

    private fun loadVaultFiles(vaultUri: Uri) {
        scope.launch {
            try {
                val newFiles = mutableListOf<VaultFileItem>()
                withContext(Dispatchers.IO) {
                    val docFile = DocumentFile.fromTreeUri(this@WidgetConfigActivity, vaultUri)
                    docFile?.let { scanForMarkdownFiles(it, "", newFiles) }
                }
                vaultFiles.clear()
                vaultFiles.addAll(newFiles)
                filteredFiles = vaultFiles
                updateAdapter()
            } catch (e: Exception) {
                Log.e("WidgetConfig", "Error loading vault files", e)
                Toast.makeText(this@WidgetConfigActivity, "Error loading files: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scanForMarkdownFiles(dir: DocumentFile, parentPath: String, collectedFiles: MutableList<VaultFileItem>) {
        val files = dir.listFiles()
        files.forEach { file ->
            val relativePath = if (parentPath.isEmpty()) {
                file.name ?: ""
            } else {
                "$parentPath/${file.name ?: ""}"
            }

            if (file.isDirectory) {
                val name = file.name ?: ""
                // Skip .obsidian and .trash directories
                if (!name.startsWith(".")) {
                    scanForMarkdownFiles(file, relativePath, collectedFiles)
                }
            } else if (file.name?.endsWith(".md", ignoreCase = true) == true) {
                collectedFiles.add(
                    VaultFileItem(
                        displayName = relativePath.removeSuffix(".md"),
                        relativePath = relativePath,
                        uri = file.uri,
                        lastModified = file.lastModified()
                    )
                )
            }
        }
    }

    private fun saveConfiguration() {
        Log.d("WidgetConfig", "saveConfiguration called, appWidgetId=$appWidgetId")
        val vaultName = etVaultName.text.toString().trim()
        if (vaultName.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_select_vault_first), Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedVaultUri == null) {
            Toast.makeText(this, getString(R.string.msg_select_vault_first), Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("obsidian_vault_uri", selectedVaultUri.toString())
            .putString("obsidian_vault_path", tvVaultPath.text.toString())
            .putString("obsidian_vault_name", vaultName)
            .apply()

        // Add selected notes on a background thread, then finish
        val repository = NoteRepository(this)
        // Collect all selected files (from vaultFiles, not just what's visible in ListView)
        val selectedFiles = mutableListOf<VaultFileItem>()
        for (file in vaultFiles) {
            if (selectedUris.contains(file.relativePath)) {
                selectedFiles.add(file)
            }
        }

        // Limit to 20 notes
        val finalSelection = selectedFiles.take(20)

        scope.launch {
            withContext(Dispatchers.IO) {
                val newNotes = mutableListOf<NoteModel>()
                for (fileItem in finalSelection) {
                    try {
                        val content = readFileContent(fileItem.uri)
                        val note = NoteModel(
                            id = UUID.randomUUID().toString(),
                            title = fileItem.displayName.substringAfterLast("/"),
                            content = content.take(2000),
                            obsidianUri = fileItem.relativePath,
                            lastModified = fileItem.lastModified
                        )
                        newNotes.add(note)
                    } catch (_: Exception) {
                        // Skip files that can't be read
                    }
                }
                
                // Replace all notes in the repository
                repository.clearAllNotes()
                for (note in newNotes) {
                    repository.addNote(note)
                }
            }

            // Trigger widget update
            Log.d("WidgetConfig", "Setting result OK for widget $appWidgetId")
            
            // If standalone, we should notify ALL widgets
            if (isStandaloneMode) {
                val appWidgetManager = AppWidgetManager.getInstance(this@WidgetConfigActivity)
                val ids = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(this@WidgetConfigActivity, NoteWidgetProvider::class.java)
                )
                for (id in ids) {
                    NoteWidgetProvider.updateWidget(this@WidgetConfigActivity, id)
                }
            } else if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                NoteWidgetProvider.updateWidget(this@WidgetConfigActivity, appWidgetId)

                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
            }

            Toast.makeText(this@WidgetConfigActivity, getString(R.string.msg_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun readFileContent(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private data class VaultFileItem(
        val displayName: String,
        val relativePath: String,
        val uri: Uri,
        val lastModified: Long
    )
}
