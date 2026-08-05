package com.talkietalkie

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMISSION = 1001
        private const val REQ_PICK = 1002
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("talkie", MODE_PRIVATE)

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            val name = findViewById<EditText>(R.id.usernameInput).text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Choisis un username d'abord", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("username", name).apply()
            showScreen(R.id.pfpLayout)
        }

        findViewById<Button>(R.id.btnPfp).setOnClickListener {
            requestGalleryAccess()
        }
    }

    private fun showScreen(layoutId: Int) {
        findViewById<LinearLayout>(R.id.usernameLayout).visibility =
            if (layoutId == R.id.usernameLayout) View.VISIBLE else View.GONE
        findViewById<LinearLayout>(R.id.pfpLayout).visibility =
            if (layoutId == R.id.pfpLayout) View.VISIBLE else View.GONE
        findViewById<LinearLayout>(R.id.loadingLayout).visibility =
            if (layoutId == R.id.loadingLayout) View.VISIBLE else View.GONE
    }

    private fun galleryPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasGalleryAccess(): Boolean =
        checkSelfPermission(galleryPermission()) == PackageManager.PERMISSION_GRANTED

    private fun requestGalleryAccess() {
        if (hasGalleryAccess()) {
            openPhotoPicker()
        } else {
            requestPermissions(arrayOf(galleryPermission()), REQ_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openPhotoPicker()
            } else {
                Toast.makeText(this, "Accès refusé", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openPhotoPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        startActivityForResult(intent, REQ_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri == null) {
                Toast.makeText(this, "Aucune photo choisie", Toast.LENGTH_SHORT).show()
                return
            }
            findViewById<ImageView>(R.id.pfpPreview).setImageURI(uri)
            prefs.edit().putString("pfp", uri.toString()).apply()
            startSync()
        }
    }

    private fun startSync() {
        showScreen(R.id.loadingLayout)
        Thread {
            Uploader(this).run()
        }.start()
    }
}
