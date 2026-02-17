package com.obsidianwidget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object ObsidianLauncher {

    private const val OBSIDIAN_PACKAGE = "md.obsidian"

    fun openNote(context: Context, vaultName: String, notePath: String) {
        val encodedVault = URLEncoder.encode(vaultName, "UTF-8").replace("+", "%20")
        val encodedPath = URLEncoder.encode(notePath, "UTF-8").replace("+", "%20")
        val uri = Uri.parse("obsidian://open?vault=$encodedVault&file=$encodedPath")

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Obsidian is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun isObsidianInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(OBSIDIAN_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
