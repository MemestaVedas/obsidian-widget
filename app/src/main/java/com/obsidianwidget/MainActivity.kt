package com.obsidianwidget

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvVaultSubtitle: TextView
    private lateinit var tvNotesSubtitle: TextView
    private lateinit var cardVault: LinearLayout
    private lateinit var cardNotes: LinearLayout
    private lateinit var layoutInfoRow: View
    private lateinit var tvInfoFiles: TextView
    private lateinit var tvInfoFolders: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_vault_status)
        tvVaultSubtitle = findViewById(R.id.tv_vault_subtitle)
        tvNotesSubtitle = findViewById(R.id.tv_notes_subtitle)
        cardVault = findViewById(R.id.card_vault)
        cardNotes = findViewById(R.id.card_notes)
        layoutInfoRow = findViewById(R.id.layout_info_row)
        tvInfoFiles = findViewById(R.id.tv_info_files)
        tvInfoFolders = findViewById(R.id.tv_info_folders)

        cardVault.setOnClickListener {
            startActivity(Intent(this, VaultSetupActivity::class.java))
            overridePendingTransition(R.anim.glass_slide_in_right, R.anim.glass_slide_out_left)
        }

        cardNotes.setOnClickListener {
            val intent = Intent(this, WidgetConfigActivity::class.java).apply {
                putExtra("mode", "standalone")
            }
            startActivity(intent)
            overridePendingTransition(R.anim.glass_slide_in_right, R.anim.glass_slide_out_left)
        }

        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        updateVaultStatus()
        updateNotesStatus()
    }

    private fun updateVaultStatus() {
        val prefs = getSharedPreferences("obsidian_widget_prefs", MODE_PRIVATE)
        val vaultName = prefs.getString("obsidian_vault_name", null)
        val vaultUri = prefs.getString("obsidian_vault_uri", null)

        if (vaultName != null && vaultUri != null) {
            // Connected state
            tvStatus.text = getString(R.string.status_connected, vaultName)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.app_success))
            tvStatus.setBackgroundResource(R.drawable.bg_status_chip_connected)

            tvVaultSubtitle.text = getString(R.string.main_vault_connected_to, vaultName)

            // Show info row with file/folder counts
            val mdCount = prefs.getInt("obsidian_vault_md_count", -1)
            val folderCount = prefs.getInt("obsidian_vault_folder_count", -1)
            if (mdCount >= 0) {
                tvInfoFiles.text = getString(R.string.main_info_files, mdCount)
                tvInfoFolders.text = getString(R.string.main_info_folders, folderCount)
                showView(layoutInfoRow)
            } else {
                hideView(layoutInfoRow)
            }

            showView(cardNotes)
        } else {
            // Disconnected state
            tvStatus.text = getString(R.string.status_not_connected)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.app_warning))
            tvStatus.setBackgroundResource(R.drawable.bg_status_chip_disconnected)

            tvVaultSubtitle.text = getString(R.string.main_vault_not_connected)
            hideView(layoutInfoRow)
            hideView(cardNotes)
        }
    }

    private fun updateNotesStatus() {
        val repository = NoteRepository(this)
        val noteCount = repository.getAllNotes().size
        tvNotesSubtitle.text = if (noteCount > 0) {
            getString(R.string.main_notes_subtitle_count, noteCount)
        } else {
            getString(R.string.main_notes_subtitle_empty)
        }
    }

    private fun showView(view: View) {
        if (view.visibility == View.VISIBLE) return
        view.alpha = 0f
        view.translationY = 12f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .start()
    }

    private fun hideView(view: View) {
        if (view.visibility != View.VISIBLE) return
        view.animate()
            .alpha(0f)
            .translationY(8f)
            .setDuration(160)
            .withEndAction { view.visibility = View.GONE }
            .start()
    }

    private fun animateEntrance() {
        val scrollContent = findViewById<View>(R.id.scroll_content)
        scrollContent.alpha = 0f
        scrollContent.translationY = 16f
        scrollContent.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .start()
    }
}
