package com.obsidianwidget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
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

/**
 * Activity to set up persistent access to Obsidian vault.
 * This stores the vault URI with persistent permissions.
 */
class VaultSetupActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var tvStatus: TextView
    private lateinit var btnSelectVault: Button
    private lateinit var btnTestAccess: Button

    private val openDocumentTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleVaultSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault_setup)

        tvStatus = findViewById(R.id.tv_vault_status)
        btnSelectVault = findViewById(R.id.btn_select_vault)
        btnTestAccess = findViewById(R.id.btn_test_access)

        btnSelectVault.setOnClickListener {
            openDocumentTree.launch(null)
        }

        btnTestAccess.setOnClickListener {
            testVaultAccess()
        }

        loadExistingVault()
        animateEntrance()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.glass_slide_in_left, R.anim.glass_slide_out_right)
    }

    private fun loadExistingVault() {
        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        val vaultUri = prefs.getString("obsidian_vault_uri", null)
        val vaultName = prefs.getString("obsidian_vault_name", null)

        if (vaultUri != null && vaultName != null) {
            val shortUri = Uri.parse(vaultUri).lastPathSegment ?: "-"
            tvStatus.text = getString(R.string.vault_status_connected, vaultName, shortUri)
            updateTestButtonState(true)
        } else {
            tvStatus.text = getString(R.string.vault_status_not_configured)
            updateTestButtonState(false)
        }
    }

    private fun handleVaultSelected(uri: Uri) {
        Log.d("VaultSetup", "Selected vault URI: $uri")

        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, takeFlags)

        val docFile = DocumentFile.fromTreeUri(this, uri)
        val vaultName = docFile?.name ?: "Unknown"

        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("obsidian_vault_uri", uri.toString())
            .putString("obsidian_vault_name", vaultName)
            .apply()

        val shortUri = uri.lastPathSegment ?: "-"
        tvStatus.text = getString(R.string.vault_status_connected, vaultName, shortUri)
        updateTestButtonState(true)

        Toast.makeText(this, "Vault access granted: $vaultName", Toast.LENGTH_SHORT).show()

        scope.launch {
            scanVaultStructure(uri)
        }
    }

    private suspend fun scanVaultStructure(vaultUri: Uri) {
        withContext(Dispatchers.IO) {
            val docFile = DocumentFile.fromTreeUri(this@VaultSetupActivity, vaultUri)
                ?: return@withContext

            val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)

            val obsidianFolder = docFile.listFiles().find { it.name == ".obsidian" && it.isDirectory }
            if (obsidianFolder != null) {
                Log.d("VaultSetup", "Found .obsidian folder")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VaultSetupActivity, "Valid Obsidian vault recognized!", Toast.LENGTH_SHORT).show()
                }
                prefs.edit().putString("obsidian_config_uri", obsidianFolder.uri.toString()).apply()

                parseObsidianSettings(obsidianFolder)
            } else {
                Log.w("VaultSetup", "No .obsidian folder found - this may not be a valid Obsidian vault")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VaultSetupActivity, "Warning: '.obsidian' folder not found. Is this a vault?", Toast.LENGTH_LONG).show()
                }
            }

            val stats = countFilesAndFolders(docFile)
            Log.d("VaultSetup", "Found ${stats.first} markdown files and ${stats.second} folders")
            prefs.edit()
                .putInt("obsidian_vault_md_count", stats.first)
                .putInt("obsidian_vault_folder_count", stats.second)
                .apply()
        }
    }

    private fun parseObsidianSettings(obsidianFolder: DocumentFile) {
        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        val editor = prefs.edit()

        val appearanceFile = obsidianFolder.listFiles().find { it.name == "appearance.json" }
        if (appearanceFile != null) {
            try {
                contentResolver.openInputStream(appearanceFile.uri)?.use { stream ->
                    val content = stream.bufferedReader().readText()

                    val cssTheme = content.substringAfter("\"cssTheme\"", "")
                        .substringAfter(":", "")
                        .substringAfter("\"", "")
                        .substringBefore("\"", "")
                        .trim()

                    if (cssTheme.isNotEmpty()) {
                        editor.putString("obsidian_active_theme", cssTheme)
                        Log.d("VaultSetup", "Active theme: $cssTheme")
                    } else {
                        editor.putString("obsidian_active_theme", "Default")
                    }

                    editor.putString("obsidian_appearance_json", content)
                }
            } catch (e: Exception) {
                Log.e("VaultSetup", "Failed to parse appearance.json", e)
                editor.putString("obsidian_active_theme", "Default (Error)")
            }
        } else {
            editor.putString("obsidian_active_theme", "Default")
        }

        val themesFolder = obsidianFolder.listFiles().find { it.name == "themes" && it.isDirectory }
        if (themesFolder != null) {
            val themeFiles = mutableListOf<String>()
            for (file in themesFolder.listFiles()) {
                if (file.isDirectory) {
                    val name = file.name ?: "Unknown"
                    if (name != "Unknown") {
                        themeFiles.add(name)
                    }
                }
            }

            editor.putString("obsidian_available_themes", themeFiles.joinToString(","))
            Log.d("VaultSetup", "Available themes: $themeFiles")
        } else {
            editor.putString("obsidian_available_themes", "")
        }

        val snippetsFolder = obsidianFolder.listFiles().find { it.name == "snippets" && it.isDirectory }
        if (snippetsFolder != null) {
            val snippets = mutableListOf<String?>()
            val snippetFiles = snippetsFolder.listFiles()
            if (snippetFiles != null) {
                for (file in snippetFiles) {
                    if (file.name?.endsWith(".css") == true) {
                        snippets.add(file.name)
                    }
                }
            }
            Log.d("VaultSetup", "Available CSS snippets: $snippets")
        }

        editor.apply()
    }

    private fun countFilesAndFolders(dir: DocumentFile): Pair<Int, Int> {
        var mdCount = 0
        var folderCount = 0
        val files = dir.listFiles()

        for (file in files) {
            if (file.isDirectory && !file.name!!.startsWith(".")) {
                folderCount++
                val subStats = countFilesAndFolders(file)
                mdCount += subStats.first
                folderCount += subStats.second
            } else if (file.name?.endsWith(".md", ignoreCase = true) == true) {
                mdCount++
            }
        }
        return Pair(mdCount, folderCount)
    }

    private fun testVaultAccess() {
        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        val vaultUri = prefs.getString("obsidian_vault_uri", null)

        if (vaultUri == null) {
            Toast.makeText(this, "No vault configured", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            try {
                val docFile = withContext(Dispatchers.IO) {
                    DocumentFile.fromTreeUri(this@VaultSetupActivity, Uri.parse(vaultUri))
                }

                if (docFile == null || !docFile.canRead()) {
                    Toast.makeText(this@VaultSetupActivity, "Cannot access vault - permission lost", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val files = withContext(Dispatchers.IO) {
                    val list = mutableListOf<String?>()
                    docFile.listFiles().take(5).forEach { list.add(it.name) }
                    list
                }

                Toast.makeText(
                    this@VaultSetupActivity,
                    "Vault accessible! Found files: $files",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e("VaultSetup", "Test access failed", e)
                Toast.makeText(this@VaultSetupActivity, "Access failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTestButtonState(enabled: Boolean) {
        btnTestAccess.isEnabled = enabled
        btnTestAccess.alpha = if (enabled) 1f else 0.62f
    }

    private fun animateEntrance() {
        val views = listOf(
            findViewById<View>(R.id.card_setup_intro),
            findViewById<View>(R.id.card_setup_status),
            btnSelectVault,
            btnTestAccess
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 20f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260)
                .setStartDelay((index * 80).toLong())
                .start()
        }
    }

    companion object {
        fun launch(activity: Activity, requestCode: Int = 1001) {
            val intent = Intent(activity, VaultSetupActivity::class.java)
            activity.startActivityForResult(intent, requestCode)
        }
    }
}

