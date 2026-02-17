package com.obsidianwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
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
    private lateinit var etSearchNotes: EditText
    private lateinit var tvSelectionCount: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var progressLoading: View

    private lateinit var fileAdapter: VaultFileAdapter
    private var isStandaloneMode = false
    private var filteredFiles: List<VaultFileItem> = emptyList()
    private val selectedUris = mutableSetOf<String>()
    private var isLoadingFiles = false

    private val openDocumentTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        scope.launch {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                selectedVaultUri = uri

                val folderName = withContext(Dispatchers.IO) {
                    val docFile = DocumentFile.fromTreeUri(this@WidgetConfigActivity, uri)
                    val name = docFile?.name ?: "Unknown"

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
                updateSaveState()
            } catch (e: Exception) {
                Log.e("WidgetConfig", "Error setting up vault", e)
                Toast.makeText(this@WidgetConfigActivity, "Error accessing folder: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        applyEntranceAnimations()

        updateSelectionState()
        updateSaveState()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.glass_slide_in_left, R.anim.glass_slide_out_right)
    }

    private fun initViews() {
        btnSelectVault = findViewById(R.id.btn_select_vault)
        tvVaultPath = findViewById(R.id.tv_vault_path)
        etVaultName = findViewById(R.id.et_vault_name)
        lvVaultFiles = findViewById(R.id.lv_vault_files)
        btnSaveConfig = findViewById(R.id.btn_save_config)
        etSearchNotes = findViewById(R.id.et_search_notes)
        tvSelectionCount = findViewById(R.id.tv_selection_count)
        tvEmptyState = findViewById(R.id.tv_empty_state)
        progressLoading = findViewById(R.id.progress_loading)

        fileAdapter = VaultFileAdapter()
        lvVaultFiles.adapter = fileAdapter
    }

    private fun setupListeners() {
        btnSelectVault.setOnClickListener {
            openDocumentTree.launch(null)
        }

        btnSaveConfig.setOnClickListener {
            saveConfiguration()
        }

        etSearchNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterFiles(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etVaultName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        lvVaultFiles.setOnItemClickListener { parent, view, position, _ ->
            if (position !in filteredFiles.indices) return@setOnItemClickListener
            val file = filteredFiles[position]
            val alreadySelected = selectedUris.contains(file.relativePath)

            if (alreadySelected) {
                selectedUris.remove(file.relativePath)
            } else {
                if (selectedUris.size >= MAX_NOTE_SELECTION) {
                    Toast.makeText(
                        this,
                        getString(R.string.msg_max_notes_reached, MAX_NOTE_SELECTION),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnItemClickListener
                }
                selectedUris.add(file.relativePath)
            }

            view.animate().cancel()
            view.scaleX = 0.98f
            view.scaleY = 0.98f
            view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()

            fileAdapter.notifyDataSetChanged()
            updateSelectionState()
            updateSaveState()
            parent.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun filterFiles(query: String) {
        filteredFiles = if (query.isBlank()) {
            vaultFiles
        } else {
            val normalizedQuery = query.trim().lowercase()
            vaultFiles.filter {
                it.displayName.lowercase().contains(normalizedQuery) ||
                    it.relativePath.lowercase().contains(normalizedQuery)
            }
        }
        updateAdapter()
    }

    private fun updateAdapter() {
        fileAdapter.notifyDataSetChanged()

        if (isLoadingFiles) {
            lvVaultFiles.visibility = View.INVISIBLE
            tvEmptyState.visibility = View.GONE
            return
        }

        lvVaultFiles.visibility = View.VISIBLE
        if (filteredFiles.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            tvEmptyState.text = if (vaultFiles.isEmpty()) {
                getString(R.string.empty_notes_state)
            } else {
                getString(R.string.empty_search_state)
            }
        } else {
            tvEmptyState.visibility = View.GONE
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
        val repository = NoteRepository(this)
        repository.getAllNotes().forEach { selectedUris.add(it.obsidianUri) }

        if (existingUri != null) {
            selectedVaultUri = Uri.parse(existingUri)
            loadVaultFiles(Uri.parse(existingUri))
        }
    }

    private fun loadVaultFiles(vaultUri: Uri) {
        scope.launch {
            setLoadingState(true)
            try {
                val newFiles = mutableListOf<VaultFileItem>()
                withContext(Dispatchers.IO) {
                    val docFile = DocumentFile.fromTreeUri(this@WidgetConfigActivity, vaultUri)
                    docFile?.let { scanForMarkdownFiles(it, "", newFiles) }
                }

                newFiles.sortBy { it.displayName.lowercase() }
                vaultFiles.clear()
                vaultFiles.addAll(newFiles)

                val validPaths = vaultFiles.map { it.relativePath }.toSet()
                selectedUris.retainAll(validPaths)

                filteredFiles = vaultFiles
                updateAdapter()
                updateSelectionState()
                updateSaveState()
            } catch (e: Exception) {
                Log.e("WidgetConfig", "Error loading vault files", e)
                Toast.makeText(this@WidgetConfigActivity, "Error loading files: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoadingState(false)
            }
        }
    }

    private fun scanForMarkdownFiles(
        dir: DocumentFile,
        parentPath: String,
        collectedFiles: MutableList<VaultFileItem>
    ) {
        val files = dir.listFiles()
        files.forEach { file ->
            val relativePath = if (parentPath.isEmpty()) {
                file.name ?: ""
            } else {
                "$parentPath/${file.name ?: ""}"
            }

            if (file.isDirectory) {
                val name = file.name ?: ""
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

        if (selectedUris.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_select_notes_required), Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("obsidian_vault_uri", selectedVaultUri.toString())
            .putString("obsidian_vault_path", tvVaultPath.text.toString())
            .putString("obsidian_vault_name", vaultName)
            .apply()

        val repository = NoteRepository(this)
        val selectedFiles = vaultFiles.filter { selectedUris.contains(it.relativePath) }
        val finalSelection = selectedFiles.take(MAX_NOTE_SELECTION)

        scope.launch {
            setLoadingState(true)
            try {
                withContext(Dispatchers.IO) {
                    val newNotes = mutableListOf<NoteModel>()
                    for (fileItem in finalSelection) {
                        try {
                            val content = readFileContent(fileItem.uri)
                            val note = NoteModel(
                                id = UUID.randomUUID().toString(),
                                title = fileItem.displayName.substringAfterLast("/"),
                                content = content.take(50000),
                                obsidianUri = fileItem.relativePath,
                                lastModified = fileItem.lastModified
                            )
                            newNotes.add(note)
                        } catch (_: Exception) {
                            // Skip files that can't be read
                        }
                    }

                    repository.clearAllNotes()
                    for (note in newNotes) {
                        repository.addNote(note)
                    }
                }

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
            } finally {
                setLoadingState(false)
            }
        }
    }

    private fun readFileContent(uri: Uri): String {
        return contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun updateSelectionState() {
        val selectedCount = selectedUris.size.coerceAtMost(MAX_NOTE_SELECTION)
        tvSelectionCount.text = getString(R.string.selection_count_dynamic, selectedCount)
        btnSaveConfig.text = if (isStandaloneMode) {
            getString(R.string.btn_update_config_dynamic, selectedCount)
        } else {
            getString(R.string.btn_save_config_dynamic, selectedCount)
        }
    }

    private fun updateSaveState() {
        val isReady = selectedVaultUri != null &&
            etVaultName.text.toString().trim().isNotEmpty() &&
            selectedUris.isNotEmpty() &&
            !isLoadingFiles

        btnSaveConfig.isEnabled = isReady
        btnSaveConfig.alpha = if (isReady) 1f else 0.62f
    }

    private fun setLoadingState(isLoading: Boolean) {
        isLoadingFiles = isLoading
        progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        updateAdapter()
        updateSaveState()
    }

    private fun applyEntranceAnimations() {
        val views = listOf(
            findViewById<View>(R.id.card_config_header),
            findViewById<View>(R.id.card_vault_info),
            findViewById<View>(R.id.card_note_selection),
            btnSaveConfig
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 20f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(280)
                .setStartDelay((index * 75).toLong())
                .start()
        }
    }

    private inner class VaultFileAdapter : BaseAdapter() {
        override fun getCount(): Int = filteredFiles.size

        override fun getItem(position: Int): Any = filteredFiles[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: ViewHolder
            val itemView: View

            if (convertView == null) {
                itemView = layoutInflater.inflate(R.layout.item_config_note, parent, false)
                holder = ViewHolder(
                    root = itemView.findViewById(R.id.item_root),
                    title = itemView.findViewById(R.id.tv_note_title),
                    path = itemView.findViewById(R.id.tv_note_path),
                    checkIcon = itemView.findViewById(R.id.iv_note_check)
                )
                itemView.tag = holder
            } else {
                itemView = convertView
                holder = convertView.tag as ViewHolder
            }

            val file = filteredFiles[position]
            val isSelected = selectedUris.contains(file.relativePath)

            holder.title.text = file.displayName.substringAfterLast("/")
            val parentPath = file.displayName.substringBeforeLast("/", "")
            holder.path.text = if (parentPath.isBlank()) {
                getString(R.string.vault_root_label)
            } else {
                parentPath
            }

            holder.root.isActivated = isSelected
            holder.checkIcon.setImageResource(
                if (isSelected) R.drawable.ic_check_box else R.drawable.ic_check_box_outline_blank
            )
            holder.checkIcon.alpha = if (isSelected) 1f else 0.8f

            return itemView
        }
    }

    private data class ViewHolder(
        val root: View,
        val title: TextView,
        val path: TextView,
        val checkIcon: ImageView
    )

    private data class VaultFileItem(
        val displayName: String,
        val relativePath: String,
        val uri: Uri,
        val lastModified: Long
    )

    companion object {
        private const val MAX_NOTE_SELECTION = 20
    }
}

