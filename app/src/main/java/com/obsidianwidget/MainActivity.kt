package com.obsidianwidget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvFileCount: TextView
    private lateinit var tvFolderCount: TextView
    private lateinit var tvCurrentTheme: TextView
    private lateinit var layoutStats: android.view.View
    private lateinit var layoutTheme: android.view.View
    private lateinit var btnSetup: Button
    private lateinit var btnManageNotes: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_vault_status)
        tvFileCount = findViewById(R.id.tv_file_count)
        tvFolderCount = findViewById(R.id.tv_folder_count)
        tvCurrentTheme = findViewById(R.id.tv_current_theme)
        layoutStats = findViewById(R.id.layout_stats)
        layoutTheme = findViewById(R.id.layout_theme)
        btnSetup = findViewById(R.id.btn_setup_vault)
        btnManageNotes = findViewById(R.id.btn_manage_notes)

        btnSetup.setOnClickListener {
            val intent = Intent(this, VaultSetupActivity::class.java)
            startActivity(intent)
        }

        btnManageNotes.setOnClickListener {
            val intent = Intent(this, WidgetConfigActivity::class.java).apply {
                putExtra("mode", "standalone")
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateVaultStatus()
    }

    private fun updateVaultStatus() {
        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        val vaultName = prefs.getString("obsidian_vault_name", null)
        val vaultUri = prefs.getString("obsidian_vault_uri", null)

        if (vaultName != null && vaultUri != null) {
            tvStatus.text = "Connected to: $vaultName"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))

            // Update Stats
            val mdCount = prefs.getInt("obsidian_vault_md_count", -1)
            val folderCount = prefs.getInt("obsidian_vault_folder_count", -1)
            
            if (mdCount >= 0) {
                layoutStats.visibility = android.view.View.VISIBLE
                tvFileCount.text = "Files: $mdCount"
                tvFolderCount.text = "Folders: $folderCount"
            }

            // Update Theme Info
            val activeTheme = prefs.getString("obsidian_active_theme", "Default")
            layoutTheme.visibility = android.view.View.VISIBLE
            tvCurrentTheme.text = "Active Theme: $activeTheme"
            btnManageNotes.visibility = android.view.View.VISIBLE

        } else {
            tvStatus.text = "Not Connected"
            tvStatus.setTextColor(getColor(android.R.color.darker_gray))
            layoutStats.visibility = android.view.View.GONE
            layoutTheme.visibility = android.view.View.GONE
            btnManageNotes.visibility = android.view.View.GONE
        }
    }
}
